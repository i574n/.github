import tu_io
import tu_feature
import gleam/string
import tu_model.{type SpiralRef, SpiralRef, type SpiralRefCell}
import gleam/io
@external(erlang, "tu_ffi", "ref_new")
@external(javascript, "./tu_ffi.mjs", "ref_new")
pub fn spiral_ref_cell_new(value: a) -> SpiralRefCell(a)

@external(erlang, "tu_ffi", "ref_get")
@external(javascript, "./tu_ffi.mjs", "ref_get")
pub fn spiral_ref_cell_get(cell: SpiralRefCell(a)) -> a

@external(erlang, "tu_ffi", "ref_set")
@external(javascript, "./tu_ffi.mjs", "ref_set")
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

pub type Mut0 { Mut0(l0 : String) }
pub fn format_real_0(v0: Int) -> String {
    let v2 = spiral_ref_new(Mut0(l0: ""))
    let v3 = string.inspect(v0)
    let v4 = spiral_ref_get(v2).l0
    let v5 = { v4 } <> { v3 }
    spiral_ref_set(v2, Mut0(l0: v5))
    spiral_ref_get(v2).l0
}
pub fn main() {
    let v0 = "typed-units-input.txt"
    tu_io.tu_io_write_input(v0, "square\ntriangle\ncircle")
    let v2 = tu_io.tu_io_read_input(v0)
    let v3 = tu_feature.tu_feature_total_sides(v2)
    let v4 = format_real_0(v3)
    let v5 = { "sides " } <> { v4 }
    let v6 = { v5 } <> { " first " }
    let v8 = tu_model.tu_model_shape_of_word("square")
    let v9 = tu_model.tu_model_shape_text(v8)
    let v10 = { v6 } <> { v9 }
    io.println(v10)
    0
}
