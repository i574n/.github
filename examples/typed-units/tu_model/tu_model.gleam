import gleam/string
pub type SpiralRefCell(a)

pub type SpiralRef(a) {
  SpiralRef(cell: SpiralRefCell(a))
}

@external(erlang, "tu_model_ffi", "ref_new")
@external(javascript, "./tu_model_ffi.mjs", "ref_new")
pub fn spiral_ref_cell_new(value: a) -> SpiralRefCell(a)

@external(erlang, "tu_model_ffi", "ref_get")
@external(javascript, "./tu_model_ffi.mjs", "ref_get")
pub fn spiral_ref_cell_get(cell: SpiralRefCell(a)) -> a

@external(erlang, "tu_model_ffi", "ref_set")
@external(javascript, "./tu_model_ffi.mjs", "ref_set")
pub fn spiral_ref_cell_set(cell: SpiralRefCell(a), value: a) -> Nil

pub fn spiral_ref_new(value: a) -> SpiralRef(a) {
  SpiralRef(cell: spiral_ref_cell_new(value))
}

pub fn spiral_ref_get(reference: SpiralRef(a)) -> a {
  spiral_ref_cell_get(reference.cell)
}

pub fn spiral_ref_set(reference: SpiralRef(a), value: a) -> Nil {
  spiral_ref_cell_set(reference.cell, value)
}

pub type Shape {
    ShapeSquare
    ShapeTriangle
    ShapeCircle
    ShapeUnknown(f3i0 : String)
}
pub type Mut0 { Mut0(l0 : String) }
pub fn closure0(capt: Nil) -> fn(Shape) -> Shape {
    fn (v0) {
        v0
    }
}
pub fn closure1(capt: Nil) -> fn(Shape) -> Int {
    fn (v0) {
        case v0  {
            ShapeCircle -> 0
            ShapeSquare -> 4
            ShapeTriangle -> 3
            ShapeUnknown(v1) -> 0
        }
    }
}
pub fn closure2(capt: Nil) -> fn(String) -> Shape {
    fn (v0) {
        let v1 = v0 == "square"
        case v1 {
            True -> ShapeSquare
            False -> {
                let v3 = v0 == "triangle"
                case v3 {
                    True -> ShapeTriangle
                    False -> {
                        let v5 = v0 == "circle"
                        case v5 {
                            True -> ShapeCircle
                            False -> ShapeUnknown(v0)
                        }
                    }
                }
            }
        }
    }
}
pub fn format_real_0(v0: Int) -> String {
    let v2 = spiral_ref_new(Mut0(l0: ""))
    let v3 = string.inspect(v0)
    let v4 = spiral_ref_get(v2).l0
    let v5 = { v4 } <> { v3 }
    spiral_ref_set(v2, Mut0(l0: v5))
    spiral_ref_get(v2).l0
}
pub fn closure3(capt: Nil) -> fn(Shape) -> String {
    fn (v0) {
        case v0  {
            ShapeCircle -> {
                "curve circle"
            }
            ShapeSquare -> {
                let v2 = format_real_0(4)
                let v3 = { "polygon " } <> { v2 }
                v3
            }
            ShapeTriangle -> {
                let v5 = format_real_0(3)
                let v6 = { "polygon " } <> { v5 }
                v6
            }
            ShapeUnknown(v8) -> {
                let v9 = { "unknown " } <> { v8 }
                v9
            }
        }
    }
}
pub fn tu_model_shape_same(v0: Shape) -> Shape {
  closure0(Nil)(v0)
}
pub fn tu_model_sides_of(v0: Shape) -> Int {
  closure1(Nil)(v0)
}
pub fn tu_model_shape_of_word(v0: String) -> Shape {
  closure2(Nil)(v0)
}
pub fn tu_model_shape_text(v0: Shape) -> String {
  closure3(Nil)(v0)
}
pub fn main() {
    let _v0 = closure0(Nil)
    let _ = Nil
    let _v1 = closure1(Nil)
    let _ = Nil
    let _v2 = closure2(Nil)
    let _ = Nil
    let _v3 = closure3(Nil)
    let _ = Nil
    0
}
