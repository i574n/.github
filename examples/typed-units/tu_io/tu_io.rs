#![allow(unused_mut, unused_variables, unused_imports, unused_parens, unused_braces, unused_assignments, dead_code, non_snake_case, non_camel_case_types, unreachable_code, while_true)]
use std::cell::RefCell;
use std::rc::Rc;
fn method0(mut v0: Vec<u8>) -> Vec<u8> {
    v0
}
fn closure0() -> Rc<dyn Fn(Rc<str>) -> Rc<str>> {
    thread_local!{ static CLOSURE: Rc<dyn Fn(Rc<str>) -> Rc<str>> = Rc::new(move |mut v0: Rc<str>| -> Rc<str> {
        let v2: Result<Vec<u8>, std::io::Error> = std::fs::read(&*v0);
        let v4: Vec<u8> = v2.unwrap();
        let v5: Vec<u8> = method0(v4);
        let v7: Result<std::string::String, std::string::FromUtf8Error> = std::string::String::from_utf8(v5);
        let v9: std::string::String = v7.unwrap();
        let v11: Rc<str> = Rc::<str>::from(String::as_str(&v9));
        v11
    }); } CLOSURE.with(|closure| closure.clone())
}
fn closure1() -> Rc<dyn Fn(Rc<str>, Rc<str>) -> ()> {
    thread_local!{ static CLOSURE: Rc<dyn Fn(Rc<str>, Rc<str>) -> ()> = Rc::new(move |mut v0: Rc<str>, mut v1: Rc<str>| -> () {
        std::fs::write(&*v0, &*v1).unwrap();
        ()
    }); } CLOSURE.with(|closure| closure.clone())
}
pub fn tu_io_read_input(v0: &str) -> Rc<str> {
    closure0()(Rc::<str>::from(v0))
}
pub fn tu_io_write_input(v0: &str, v1: &str) -> () {
    closure1()(Rc::<str>::from(v0), Rc::<str>::from(v1))
}
