use std::process::ExitCode;

use tlstore_ui::app::{self, Options};
use tlstore_ui::demo::Demo;
use tlstore_ui::term::{self, Parser, Tty};

const USAGE: &str = "tlstore-ui opens the store; run tlstore to start it.

  tlstore-ui --demo    show the layout with sample rows
  tlstore-ui --probe   list what this terminal can draw
";

fn probe() -> std::io::Result<()> {
    let mut tty = Tty::open()?;
    let (caps, _) = term::probe(&mut tty, &mut Parser::new(), term::probe::PROBE_TIMEOUT);
    let size = tty.size()?;
    drop(tty);
    let yes = |b: bool| if b { "yes" } else { "no" };
    println!("pictures        {}", yes(caps.kitty_graphics));
    println!("text sizing     {}", yes(caps.text_sizing));
    println!("smooth frames   {}", yes(caps.sync_output));
    println!("resize reports  {}", yes(caps.in_band_resize));
    match caps.cell_px {
        Some((w, h)) => println!("cell            {w}x{h} px"),
        None => println!("cell            unknown"),
    }
    println!("grid            {}x{}", size.cols, size.rows);
    match caps.dark() {
        Some(d) => println!("background      {}", if d { "dark" } else { "light" }),
        None => println!("background      unknown"),
    }
    println!("answered        {}", yes(caps.answered));
    Ok(())
}

fn main() -> ExitCode {
    let arg = std::env::args().nth(1).unwrap_or_default();
    let result = match arg.as_str() {
        "--demo" => app::run(Box::new(Demo::new()), Options::default()),
        "--probe" => probe(),
        "--version" => {
            // The second line is one compile-time literal (`concat!`, not two prints that just
            // happen to run back to back) so the label and hash sit in a single contiguous
            // string in the binary's rodata — checkTlstoreUiFresh (app/build.gradle) greps the
            // built binary for it without executing it, since a device-ABI binary cannot run on
            // the build host. See scripts/tlstore/ui-src-hash.sh and build.rs.
            const SRC_HASH_LINE: &str = concat!("TLSTORE_UI_SRC_HASH=", env!("TLSTORE_UI_SRC_HASH"));
            println!("tlstore-ui {}", env!("CARGO_PKG_VERSION"));
            println!("{SRC_HASH_LINE}");
            Ok(())
        }
        _ => {
            eprint!("{USAGE}");
            return ExitCode::from(2);
        }
    };
    match result {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            eprintln!("tlstore-ui: {e}");
            ExitCode::FAILURE
        }
    }
}
