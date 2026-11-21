//! The MatchBox backend for the bx-toml module: the same four functions as the JVM module, without a JVM.

#![warn(missing_docs)]
#![warn(clippy::missing_docs_in_private_items)]

pub mod model;
pub mod parse;
mod plugin;
pub mod write;
pub mod zone;
