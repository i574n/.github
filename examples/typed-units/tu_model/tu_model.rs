#![allow(unused_mut, unused_variables, unused_imports, unused_parens, unused_braces, unused_assignments, dead_code, non_snake_case, non_camel_case_types, unreachable_code, while_true)]
use std::cell::RefCell;
use std::rc::Rc;
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
#[derive(Clone)]
pub enum shape {
    shape_Square,
    shape_Triangle,
    shape_Circle,
    shape_Unknown(Rc<str>),
}
pub struct Mut0 { pub l0: Rc<str> }
impl shape {
    pub fn tag(&self) -> i32 {
        match self {
            shape::shape_Square => 0,
            shape::shape_Triangle => 1,
            shape::shape_Circle => 2,
            shape::shape_Unknown(..) => 3,
        }
    }
}
impl Default for shape {
    fn default() -> Self {
        shape::shape_Square
    }
}
fn closure0() -> Rc<dyn Fn(shape) -> shape> {
    thread_local!{ static CLOSURE: Rc<dyn Fn(shape) -> shape> = Rc::new(move |mut v0: shape| -> shape {
        v0
    }); } CLOSURE.with(|closure| closure.clone())
}
fn closure1() -> Rc<dyn Fn(shape) -> i32> {
    thread_local!{ static CLOSURE: Rc<dyn Fn(shape) -> i32> = Rc::new(move |mut v0: shape| -> i32 {
        match v0 {
            shape::shape_Circle => {
                0i32
            }
            shape::shape_Square => {
                4i32
            }
            shape::shape_Triangle => {
                3i32
            }
            shape::shape_Unknown(v1) => {
                let v1: Rc<str> = v1;
                0i32
            }
        }
    }); } CLOSURE.with(|closure| closure.clone())
}
fn closure2() -> Rc<dyn Fn(Rc<str>) -> shape> {
    thread_local!{ static CLOSURE: Rc<dyn Fn(Rc<str>) -> shape> = Rc::new(move |mut v0: Rc<str>| -> shape {
        let v1: bool = &*v0 == "square";
        if v1 {
            shape::shape_Square
        } else {
            let v3: bool = &*v0 == "triangle";
            if v3 {
                shape::shape_Triangle
            } else {
                let v5: bool = &*v0 == "circle";
                if v5 {
                    shape::shape_Circle
                } else {
                    shape::shape_Unknown(v0)
                }
            }
        }
    }); } CLOSURE.with(|closure| closure.clone())
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
fn closure3() -> Rc<dyn Fn(shape) -> Rc<str>> {
    thread_local!{ static CLOSURE: Rc<dyn Fn(shape) -> Rc<str>> = Rc::new(move |mut v0: shape| -> Rc<str> {
        match v0 {
            shape::shape_Circle => {
                let v7: Rc<str> = { thread_local!{ static LIT: Rc<str> = Rc::<str>::from("curve circle"); } LIT.with(|lit| lit.clone()) };
                v7
            }
            shape::shape_Square => {
                let v1: i32 = 4i32;
                let v2: Rc<str> = format_real_0(v1);
                let v3: Rc<str> = string_concat(&["polygon ", &*v2]);
                v3
            }
            shape::shape_Triangle => {
                let v4: i32 = 3i32;
                let v5: Rc<str> = format_real_0(v4);
                let v6: Rc<str> = string_concat(&["polygon ", &*v5]);
                v6
            }
            shape::shape_Unknown(v8) => {
                let v8: Rc<str> = v8;
                let v9: Rc<str> = string_concat(&["unknown ", &*v8]);
                v9
            }
        }
    }); } CLOSURE.with(|closure| closure.clone())
}
pub fn tu_model_shape_same(v0: shape) -> shape {
    closure0()(v0)
}
pub fn tu_model_sides_of(v0: shape) -> i32 {
    closure1()(v0)
}
pub fn tu_model_shape_of_word(v0: &str) -> shape {
    closure2()(Rc::<str>::from(v0))
}
pub fn tu_model_shape_text(v0: shape) -> Rc<str> {
    closure3()(v0)
}
pub type SpiralShape_97df63ebabc3 = Mut0;
