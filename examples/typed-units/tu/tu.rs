#![allow(unused_mut, unused_variables, unused_imports, unused_parens, unused_braces, unused_assignments, dead_code, non_snake_case, non_camel_case_types, unreachable_code, while_true)]
use std::cell::RefCell;
use std::rc::Rc;
use tu_model::{shape};
fn string_concat(parts: &[&str]) -> Rc<str> {
    let mut joined = Rc::<[u8]>::new_uninit_slice(parts.iter().map(|part| part.len()).sum());
    let bytes = Rc::get_mut(&mut joined).unwrap().as_mut_ptr() as *mut u8;
    let mut at = 0;
    for part in parts {
        unsafe { std::ptr::copy_nonoverlapping(part.as_ptr(), bytes.add(at), part.len()) };
        at += part.len();
    }
    unsafe { Rc::from_raw(Rc::into_raw(joined.assume_init()) as *const str) }
}
fn method1(mut v0: &Rc<RefCell<Mut0>>, mut v1: Rc<str>) -> () {
    let v2: Rc<str> = v0.borrow().l0.clone();
    let v3: Rc<str> = string_concat(&[&*v2, &*v1]);
    v0.borrow_mut().l0 = v3;
    ()
}
fn format_real_0(mut v0: i32) -> Rc<str> {
    let v1: Rc<str> = { thread_local!{ static LIT: Rc<str> = Rc::<str>::from(""); } LIT.with(|lit| lit.clone()) };
    let v2: Rc<RefCell<Mut0>> = Rc::new(RefCell::new(Mut0 { l0: v1 }));
    let v3: Rc<str> = Rc::<str>::from(format!("{}", v0));
    method1(&v2, v3);
    let v4: Rc<str> = v2.borrow().l0.clone();
    v4
}
fn spiral_main() -> i32 {
    let mut v0: Rc<str> = { thread_local!{ static LIT: Rc<str> = Rc::<str>::from("typed-units-input.txt"); } LIT.with(|lit| lit.clone()) };
    let v1: Rc<str> = { thread_local!{ static LIT: Rc<str> = Rc::<str>::from("square\ntriangle\ncircle"); } LIT.with(|lit| lit.clone()) };
    tu_io::tu_io_write_input(&v0, &v1);
    let v2: Rc<str> = tu_io::tu_io_read_input(&v0);
    let v3: i32 = tu_feature::tu_feature_total_sides(&v2);
    let v4: Rc<str> = format_real_0(v3);
    let v7: Rc<str> = { thread_local!{ static LIT: Rc<str> = Rc::<str>::from("square"); } LIT.with(|lit| lit.clone()) };
    let v8: shape = tu_model::tu_model_shape_of_word(&v7);
    let v9: Rc<str> = tu_model::tu_model_shape_text(v8);
    let v10: Rc<str> = string_concat(&["sides ", &*v4, " first ", &*v9]);
    println!("{}", v10);
    0i32
}
fn main() { #[cfg(target_arch = "wasm32")] { spiral_main(); return; }
    let main = std::thread::Builder::new().stack_size(1 << 30).spawn(spiral_main).unwrap();
    std::process::exit(match main.join() { Ok(code) => code, Err(_) => 1 });
}
use tu_model::SpiralShape_97df63ebabc3 as Mut0;
