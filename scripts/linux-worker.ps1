#requires -Version 7
param(
    [Parameter(Mandatory)] [string] $SshAlias,
    [string[]] $ExemptIp,
    [string] $K3sVersion = 'v1.37.1+k3s1',
    [string] $IggyImage = 'apache/iggy:0.9.0',
    [string] $IggyMemory = '512Mi',
    [switch] $HostNetwork,
    [switch] $Tls,
    [string[]] $TlsSanIp,
    [string] $KitBinary,
    [switch] $NoK3s,
    [ValidateRange(1, 64)] [int] $Slots = 1,
    [string] $KitRoot = (Join-Path $PSScriptRoot '..\..\kit'),
    [ValidateRange(10, 3600)] [int] $ReadySeconds = 300,
    [switch] $SkipVerify,
    [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
$sshOptions = @('-o', 'BatchMode=yes', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4')
$recipe = Join-Path $KitRoot 'infra\k3s-iggy'
foreach ($file in 'bootstrap.sh', 'iggy.yaml', 'verify.ps1') {
    if (-not (Test-Path (Join-Path $recipe $file))) { throw "linux-worker.ps1: $file missing under $recipe (-KitRoot)" }
}

function Invoke-Remote([string] $label, [string] $body) {
    if ($DryRun) { return "DRY $label" }
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($body -replace "`r", '')))
    $out = ssh @sshOptions $SshAlias "echo $encoded | base64 -d | sh" 2>&1
    if ($LASTEXITCODE) { throw "linux-worker.ps1: $label failed on $SshAlias (exit $LASTEXITCODE): $($out -join ' | ')" }
    $out
}

function Edit-Line([string] $text, [string] $old, [string] $new) {
    if (-not $text.Contains($old)) { throw "linux-worker.ps1: kit's iggy.yaml no longer contains '$old'; update the patch" }
    $text.Replace($old, $new)
}

$probe = ssh @sshOptions $SshAlias 'echo ${SSH_CLIENT%% *}; uname -m; ssh -V 2>&1 | head -n 1' 2>&1
if ($LASTEXITCODE -or @($probe).Count -lt 3) { throw "linux-worker.ps1: cannot reach $SshAlias over ssh: $($probe -join ' | ')" }
$controller, $arch, $openssh = @($probe)
if (-not $ExemptIp) { $ExemptIp = @($controller) }
$penalties = $openssh -match 'OpenSSH_(\d+)\.(\d+)' -and ([int] $Matches[1] -gt 9 -or ([int] $Matches[1] -eq 9 -and [int] $Matches[2] -ge 8))
"machine: $SshAlias $arch, $openssh, controller $controller"

$sshd = @('MaxStartups 30:30:100')
if ($penalties) { $sshd = @("PerSourcePenaltyExemptList $($ExemptIp -join ',')", 'PerSourcePenalties noauth:0') + $sshd }
Invoke-Remote 'sshd drop-in' @"
set -eu
f=/etc/ssh/sshd_config.d/10-kit-workers.conf
new=`$(mktemp)
cat > "`$new" <<'EOF'
$($sshd -join "`n")
EOF
if [ -f "`$f" ] && cmp -s "`$new" "`$f"; then rm -f "`$new"; echo "sshd: unchanged"; exit 0; fi
if [ -f "`$f" ]; then cp "`$f" "`$f.bak"; fi
mv "`$new" "`$f"; chmod 644 "`$f"
if ! sshd -t; then
    if [ -f "`$f.bak" ]; then mv "`$f.bak" "`$f"; else rm -f "`$f"; fi
    echo "sshd: config rejected by sshd -t, previous file restored" >&2; exit 5
fi
systemctl reload ssh 2>/dev/null || systemctl reload sshd
echo "sshd: updated ($($sshd.Count) lines)"
"@

if (-not $NoK3s) {
$bootstrap = [IO.File]::ReadAllText((Join-Path $recipe 'bootstrap.sh'))
Invoke-Remote 'k3s bootstrap' ("K3S_VERSION='$K3sVersion'; export K3S_VERSION`n" + $bootstrap) | Select-Object -Last 1

$manifest = [IO.File]::ReadAllText((Join-Path $recipe 'iggy.yaml')) -replace "`r", ''
$manifest = Edit-Line $manifest 'image: apache/iggy:0.9.0' "image: $IggyImage"
$manifest = Edit-Line $manifest "              memory: 512Mi`n            limits:`n              cpu: 250m`n              memory: 512Mi" "              memory: $IggyMemory`n            limits:`n              cpu: 250m`n              memory: $IggyMemory"
if ($HostNetwork) {
    $manifest = Edit-Line $manifest "      enableServiceLinks: false" "      hostNetwork: true`n      dnsPolicy: ClusterFirstWithHostNet`n      enableServiceLinks: false"
}
Invoke-Remote 'iggy' @"
set -eu
k() { k3s kubectl "`$@"; }
k create namespace iggy --dry-run=client -o yaml | k apply -f - >/dev/null
k -n iggy get secret iggy-root >/dev/null 2>&1 || k -n iggy create secret generic iggy-root --from-literal=username=iggy --from-literal=password="`$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')" >/dev/null
k -n iggy get secret iggy-jwt >/dev/null 2>&1 || k -n iggy create secret generic iggy-jwt --from-literal=secret="`$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')" >/dev/null
m=`$(mktemp)
cat > "`$m" <<'EOF'
$manifest
EOF
k apply -f "`$m" | grep -v unchanged || true
rm -f "`$m"
k -n iggy rollout status deployment/iggy --timeout=${ReadySeconds}s >/dev/null
ip=`$(k -n iggy get svc iggy -o jsonpath='{.spec.clusterIP}')
deadline=`$(( `$(date +%s) + $ReadySeconds ))
until curl -fsS -m 5 "http://`$ip:3000/ping" >/dev/null 2>&1; do
    if [ "`$(date +%s)" -ge "`$deadline" ]; then echo "iggy: http://`$ip:3000/ping not answering" >&2; exit 6; fi
    sleep 3
done
echo "iggy: ready $IggyImage at `$ip (limit $IggyMemory, hostNetwork $([bool] $HostNetwork))"
"@

if ($Tls) {
    $sanExtra = ($TlsSanIp | ForEach-Object { ",IP:$_" }) -join ''
    Invoke-Remote 'iggy-tls' @"
set -eu
k() { k3s kubectl -n iggy "`$@"; }
cd /root && mkdir -p iggy-ca && cd iggy-ca
clusterip=`$(k get svc iggy -o jsonpath='{.spec.clusterIP}')
if [ ! -f ca.crt ]; then
    openssl genpkey -algorithm ED25519 -out ca.key
    openssl req -x509 -new -key ca.key -days 3650 -subj '/CN=x-dumps iggy CA' -out ca.crt
fi
printf '[req]\ndistinguished_name=dn\n[dn]\n[v3]\nsubjectAltName=DNS:iggy.iggy.svc,DNS:iggy.iggy.svc.cluster.local,DNS:localhost,IP:127.0.0.1,IP:%s$sanExtra\n' "`$clusterip" > san.cnf
openssl genpkey -algorithm ED25519 -out server.key
openssl req -new -key server.key -subj '/CN=iggy.iggy.svc' -out server.csr
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 825 -extensions v3 -extfile san.cnf -out server.crt
k delete secret iggy-tls >/dev/null 2>&1 || true
k create secret generic iggy-tls --from-file=server.crt=server.crt --from-file=server.key=server.key --from-file=ca.crt=ca.crt >/dev/null
k set env deployment/iggy IGGY_HTTP_TLS_ENABLED=true IGGY_HTTP_TLS_CERT_FILE=/certs/server.crt IGGY_HTTP_TLS_KEY_FILE=/certs/server.key IGGY_TCP_TLS_ENABLED=true IGGY_TCP_TLS_SELF_SIGNED=false IGGY_TCP_TLS_CERT_FILE=/certs/server.crt IGGY_TCP_TLS_KEY_FILE=/certs/server.key >/dev/null
cat > patch.yaml <<'EOF'
spec:
  template:
    spec:
      containers:
      - name: iggy
        readinessProbe: {httpGet: {scheme: HTTPS}}
        livenessProbe: {httpGet: {scheme: HTTPS}}
        volumeMounts:
        - {name: certs, mountPath: /certs, readOnly: true}
      volumes:
      - {name: certs, secret: {secretName: iggy-tls}}
EOF
k patch deployment/iggy --type strategic --patch-file patch.yaml >/dev/null
k rollout status deployment/iggy --timeout=${ReadySeconds}s >/dev/null
curl -fsS -m 5 --cacert ca.crt --resolve "iggy.iggy.svc.cluster.local:3000:`$clusterip" https://iggy.iggy.svc.cluster.local:3000/ping >/dev/null && echo "iggy TLS: https 3000 ok"
echo | timeout 8 openssl s_client -connect "`$clusterip":8090 -CAfile ca.crt 2>/dev/null | grep -q 'Verify return code: 0' && echo "iggy TLS: tcp 8090 ok" || echo "iggy TLS: tcp 8090 handshake unverified"
echo "iggy TLS: CA at /root/iggy-ca/ca.crt (consumers trust it via IGGY_CA_PEM); http.tls has no self_signed field, tcp.tls does"
"@
}

}

if ($KitBinary -and $NoK3s) {
    Invoke-Remote 'kit-worker (systemd)' @"
set -eu
test -x /$($KitBinary.TrimStart('/')) || { echo "kit-worker: $KitBinary is not an executable on the machine" >&2; exit 7; }
cat > /etc/systemd/system/kit-worker.service <<'EOF'
[Unit]
Description=kit job host (rig worker, no k3s)
After=network-online.target
Wants=network-online.target

[Service]
EnvironmentFile=-/etc/environment
Environment=KIT_JOB_HOST_IDLE_EXIT_SECONDS=0
Environment=KIT_JOB_SLOTS=$Slots
ExecStart=$KitBinary job host
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now kit-worker.service >/dev/null
sleep 3
systemctl is-active kit-worker.service
echo "kit-worker: running `$(pgrep -fc "^$KitBinary job host") job host(s), slots $Slots"
"@
} elseif ($KitBinary) {
    Invoke-Remote 'kit-worker' @"
set -eu
m=`$(mktemp)
cat > "`$m" <<'EOF'
apiVersion: v1
kind: Namespace
metadata:
  name: kit
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kit-worker
  namespace: kit
  labels: {app: kit-worker, lane: kit}
spec:
  replicas: 1
  strategy: {type: Recreate}
  selector: {matchLabels: {app: kit-worker}}
  template:
    metadata:
      labels: {app: kit-worker, lane: kit}
    spec:
      hostPID: true
      containers:
      - name: kit-worker
        image: docker.io/library/ubuntu:26.04
        securityContext: {privileged: true}
        env:
        - {name: KIT_JOB_HOST_IDLE_EXIT_SECONDS, value: "0"}
        - {name: KIT_JOB_SLOTS, value: "$Slots"}
        command: ["chroot", "/host", "/bin/bash", "-lc"]
        args:
        - |
          set -a; . /etc/environment; set +a
          exec $KitBinary job host
        livenessProbe:
          exec: {command: ["chroot", "/host", "pgrep", "-f", "^$KitBinary job host"]}
          initialDelaySeconds: 30
          periodSeconds: 60
        resources:
          requests: {cpu: 50m, memory: 64Mi}
        volumeMounts:
        - {name: host, mountPath: /host}
      volumes:
      - name: host
        hostPath: {path: /, type: Directory}
EOF
test -x /$($KitBinary.TrimStart('/')) || { echo "kit-worker: $KitBinary is not an executable on the machine" >&2; exit 7; }
k3s kubectl apply -f "`$m" | grep -v unchanged || true
rm -f "`$m"
k3s kubectl -n kit rollout status deployment/kit-worker --timeout=${ReadySeconds}s >/dev/null
echo "kit-worker: running `$(pgrep -fc "^$KitBinary job host") job host(s)"
"@
}

if (-not ($SkipVerify -or $DryRun -or $NoK3s)) {
    $ip = (ssh @sshOptions $SshAlias "k3s kubectl -n iggy get svc iggy -o jsonpath='{.spec.clusterIP}'").Trim()
    $port = Get-Random -Minimum 20000 -Maximum 29999
    $tunnel = Start-Process ssh -ArgumentList (@($sshOptions) + @('-N', '-o', 'ExitOnForwardFailure=yes', '-L', "${port}:${ip}:3000", $SshAlias)) -PassThru -WindowStyle Hidden
    try {
        $env:IGGY_PASSWORD = (ssh @sshOptions $SshAlias "k3s kubectl -n iggy get secret iggy-root -o jsonpath='{.data.password}' | base64 -d").Trim()
        & (Join-Path $recipe 'verify.ps1') -BaseUrl "http://127.0.0.1:$port" -Count 1000 -Batch 500 -ReadySeconds 60
        if ($LASTEXITCODE) { throw "linux-worker.ps1: iggy verify failed through the tunnel to $SshAlias" }
    } finally {
        Remove-Item Env:IGGY_PASSWORD -ErrorAction SilentlyContinue
        Stop-Process -Id $tunnel.Id -ErrorAction SilentlyContinue
    }
}
"LINUX-WORKER-READY $SshAlias"
