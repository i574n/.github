import gleam/bit_array
import gleam/dynamic.{type Dynamic}
import gleam/erlang/atom
@external(erlang, "file", "read_file")
fn spiral_fs_read_file(path: String) -> Result(BitArray, Dynamic)

@external(erlang, "file", "write_file")
fn spiral_fs_write_file(path: String, data: String) -> Dynamic

@external(erlang, "filelib", "is_regular")
fn spiral_fs_is_regular(path: String) -> Bool

@external(erlang, "gleam_stdlib", "identity")
fn spiral_fs_coerce(value: a) -> b

pub type Us0 {
    Us0Ok(f0i0 : BitArray)
    Us0Error(f1i0 : Dynamic)
}
pub type Us1 {
    Us1Ok(f0i0 : String)
    Us1Error
}
pub fn ok_c1(capt: Nil) -> fn(BitArray) -> Us0 {
    fn (v0) {
        Us0Ok(v0)
    }
}
pub fn method0() -> fn(BitArray) -> Us0 {
    ok_c1(Nil)
}
pub fn error_c2(capt: Nil) -> fn(Dynamic) -> Us0 {
    fn (v0) {
        Us0Error(v0)
    }
}
pub fn method1() -> fn(Dynamic) -> Us0 {
    error_c2(Nil)
}
pub fn ok_c3(capt: Nil) -> fn(String) -> Us1 {
    fn (v0) {
        Us1Ok(v0)
    }
}
pub fn method2() -> fn(String) -> Us1 {
    ok_c3(Nil)
}
pub fn error_c4(capt: Nil) -> fn(Nil) -> Us1 {
    fn (dom) {
        Us1Error
    }
}
pub fn method3() -> fn(Nil) -> Us1 {
    error_c4(Nil)
}
pub fn closure0(capt: Nil) -> fn(String) -> String {
    fn (v0) {
        let v1 = spiral_fs_read_file(v0)
        let v2 = method0()
        let v3 = method1()
        let v4 = case v1 { Ok(x) -> v2(x) Error(x) -> v3(x) }
        case v4  {
            Us0Error(v14) -> panic as "file_system.read_all_text: file:read_file failed"
            Us0Ok(v5) -> {
                let v6 = bit_array.to_string(v5)
                let v7 = method2()
                let v8 = method3()
                let v9 = case v6 { Ok(x) -> v7(x) Error(x) -> v8(x) }
                case v9  {
                    Us1Error -> panic as "file_system.read_all_text: the file is not UTF-8"
                    Us1Ok(v10) -> v10
                }
            }
        }
    }
}
pub fn closure5(capt: Nil) -> fn(#(String, String)) -> Nil {
    fn (dom) {
        let #(v0, v1) = dom
        let v2 = spiral_fs_write_file(v0, v1)
        let v3 = spiral_fs_coerce(v2)
        let v5 = atom.create("ok")
        let v6 = v3 == v5
        let v7 = v6 == False
        case v7 {
            True -> panic as "file_system.write_all_text: the write failed"
            False -> Nil
        }
    }
}
pub fn tu_io_read_input(v0: String) -> String {
  closure0(Nil)(v0)
}
pub fn tu_io_write_input(v0: String, v1: String) -> Nil {
  closure5(Nil)(#(v0, v1))
}
pub fn main() {
    let _v0 = closure0(Nil)
    let _ = Nil
    let _v1 = closure5(Nil)
    let _ = Nil
    0
}
