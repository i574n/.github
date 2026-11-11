import gleam/string
import tu_model
import gleam/list
import gleam/option
import gleam/int
import gleam/bit_array
pub type SpiralArrayStore(a)

pub type SpiralArray(a) {
  SpiralArray(store: SpiralArrayStore(option.Option(a)), size: Int)
}

@external(erlang, "tu_feature_ffi", "array_new")
@external(javascript, "./tu_feature_ffi.mjs", "array_new")
pub fn spiral_array_store_new(size: Int, fill: a) -> SpiralArrayStore(a)

@external(erlang, "tu_feature_ffi", "array_get")
@external(javascript, "./tu_feature_ffi.mjs", "array_get")
pub fn spiral_array_store_get(store: SpiralArrayStore(a), i: Int) -> a

@external(erlang, "tu_feature_ffi", "array_set")
@external(javascript, "./tu_feature_ffi.mjs", "array_set")
pub fn spiral_array_store_set(store: SpiralArrayStore(a), i: Int, value: a) -> Nil

pub fn spiral_array_create(size: Int) -> SpiralArray(a) {
  SpiralArray(store: spiral_array_store_new(size, option.None), size: size)
}


pub fn spiral_array_from_list(values: List(a)) -> SpiralArray(a) {
  let xs = spiral_array_create(list.length(values))
  let _ = list.index_map(values, fn(x, i) { spiral_array_set(xs, i, x) })
  xs
}


pub fn spiral_array_get(xs: SpiralArray(a), i: Int) -> a {
  case i >= 0 && i < xs.size {
    True ->
      case spiral_array_store_get(xs.store, i) {
        option.Some(x) -> x
        option.None -> panic as "spiral array: read of an element that was never set"
      }
    False -> panic as "spiral array: index out of bounds"
  }
}

pub fn spiral_array_set(xs: SpiralArray(a), i: Int, value: a) -> Nil {
  case i >= 0 && i < xs.size {
    True -> spiral_array_store_set(xs.store, i, option.Some(value))
    False -> panic as "spiral array: index out of bounds"
  }
}

pub fn spiral_wrap_signed(value: Int, bits: Int) -> Int {
  let half = int.bitwise_shift_left(1, bits - 1)
  int.bitwise_and(value + half, int.bitwise_shift_left(1, bits) - 1) - half
}


pub fn spiral_string_length(text: String) -> Int {
  bit_array.byte_size(bit_array.from_string(text))
}


pub fn spiral_string_index(text: String, index: Int) -> String {
  case bit_array.slice(bit_array.from_string(text), index, 1) {
    Ok(<<byte>>) ->
      case string.utf_codepoint(byte) {
        Ok(codepoint) -> string.from_utf_codepoints([codepoint])
        Error(_) -> panic as "spiral string index: not a single-byte character"
      }
    _ -> panic as "spiral string index: out of range"
  }
}

@external(erlang, "gleam_stdlib", "identity")
fn spiral_string_of_checked_bytes(bytes: BitArray) -> String {
  case bit_array.to_string(bytes) {
    Ok(text) -> text
    Error(_) -> panic as "spiral string slice: splits a character"
  }
}

fn spiral_string_starts_character(bytes: BitArray, at: Int) -> Bool {
  case bit_array.slice(bytes, at, 1) {
    Ok(<<lead>>) -> lead < 128 || lead >= 192
    _ -> True
  }
}

pub fn spiral_string_slice(text: String, from: Int, to: Int) -> String {
  let bytes = bit_array.from_string(text)
  let length = bit_array.byte_size(bytes)
  case from < 0 || from > length || to < from - 1 || to >= length {
    True -> panic as "spiral string slice: out of range"
    False ->
      case to < from {
        True -> ""
        False ->
          case spiral_string_starts_character(bytes, from) && spiral_string_starts_character(bytes, to + 1) {
            False -> panic as "spiral string slice: splits a character"
            True ->
              case bit_array.slice(bytes, from, to - from + 1) {
                Ok(part) -> spiral_string_of_checked_bytes(part)
                Error(_) -> panic as "spiral string slice: out of range"
              }
          }
      }
  }
}

pub fn method1(v0: String, v1: Int, v2: Int) -> Int {
    let v3 = v2 >= v1
    case v3 {
        True -> v1
        False -> {
            let v4 = spiral_string_index(v0, v2)
            let v5 = v4 == " "
            let v11 =
                case v5 {
                    True -> True
                    False -> {
                        let v6 = v4 == "\t"
                        case v6 {
                            True -> True
                            False -> {
                                let v7 = v4 == "\r"
                                case v7 {
                                    True -> True
                                    False -> {
                                        v4 == "\n"
                                    }
                                }
                            }
                        }
                    }
                }
            case v11 {
                True -> {
                    let v12 = spiral_wrap_signed(v2 + 1, 32)
                    method1(v0, v1, v12)
                }
                False -> v2
            }
        }
    }
}
pub fn method2(v0: String, v1: Int) -> Int {
    let v2 = v1 <= 0
    case v2 {
        True -> -1
        False -> {
            let v3 = spiral_wrap_signed(v1 - 1, 32)
            let v4 = spiral_string_index(v0, v3)
            let v5 = v4 == " "
            let v11 =
                case v5 {
                    True -> True
                    False -> {
                        let v6 = v4 == "\t"
                        case v6 {
                            True -> True
                            False -> {
                                let v7 = v4 == "\r"
                                case v7 {
                                    True -> True
                                    False -> {
                                        v4 == "\n"
                                    }
                                }
                            }
                        }
                    }
                }
            case v11 {
                True -> method2(v0, v3)
                False -> v3
            }
        }
    }
}
pub fn sides_from_0(v0: SpiralArray(String), v1: Int, v2: Int, v3: Int) -> Int {
    let v4 = v1 >= v2
    case v4 {
        True -> v3
        False -> {
            let v5 = spiral_wrap_signed(v1 + 1, 32)
            let v6 = spiral_array_get(v0, v1)
            let v7 = spiral_string_length(v6)
            let v9 = method1(v6, v7, 0)
            let v10 = spiral_wrap_signed(v7 - 1, 32)
            let v11 = spiral_string_slice(v6, v9, v10)
            let v12 = spiral_string_length(v11)
            let v13 = method2(v11, v12)
            let v14 = spiral_string_slice(v11, 0, v13)
            let v15 = tu_model.tu_model_shape_of_word(v14)
            let v16 = tu_model.tu_model_sides_of(v15)
            let v17 = spiral_wrap_signed(v3 + v16, 32)
            sides_from_0(v0, v5, v2, v17)
        }
    }
}
pub fn closure0(capt: Nil) -> fn(String) -> Int {
    fn (v0) {
        let v2 = v0 |> string.split("\n")
        let v3 = v2 |> spiral_array_from_list
        let v5 = v3.size
        sides_from_0(v3, 0, v5, 0)
    }
}
pub fn tu_feature_total_sides(v0: String) -> Int {
  closure0(Nil)(v0)
}
pub fn main() {
    let _v0 = closure0(Nil)
    let _ = Nil
    0
}
