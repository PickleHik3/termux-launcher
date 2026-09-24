//! The catalog as the script reports it (TSV contract, Revision 5, plus `Readme-skip`):
//! `list --tsv`, `info --tsv <name>`, `update --check --tsv`, and the progress stream.

use std::collections::HashMap;

use super::proc::Env;

/// One visible item, from `tlstore list --tsv`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Item {
    /// Catalog order, 1-based (the "NN").
    pub no: usize,
    pub name: String,
    pub version: String,
    /// Installed version, when installed.
    pub installed: Option<String>,
    pub kind: String,
    pub summary: String,
    pub category: String,
    pub featured: bool,
}

/// One update, from `tlstore update --check --tsv`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Update {
    pub name: String,
    pub have: String,
    pub new: String,
    pub note: String,
}

/// A row's status word on Front.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Status {
    /// The featured item, not installed yet.
    New,
    /// Installed, with a newer version waiting.
    Update(String),
    Installed,
    None,
}

impl Status {
    pub fn label(&self) -> String {
        match self {
            Status::New => "new".into(),
            Status::Update(v) => format!("↑ {v}"),
            Status::Installed => "installed".into(),
            Status::None => String::new(),
        }
    }
    /// New and update statuses are drawn in the accent.
    pub fn hot(&self) -> bool {
        matches!(self, Status::New | Status::Update(_))
    }
}

pub fn parse_list(tsv: &str) -> Vec<Item> {
    tsv.lines()
        .filter(|l| !l.trim().is_empty())
        .enumerate()
        .map(|(i, l)| {
            let f: Vec<&str> = l.split('\t').collect();
            let get = |k: usize| f.get(k).copied().unwrap_or("").to_string();
            let dash = |s: String| if s == "-" { String::new() } else { s };
            Item {
                no: i + 1,
                name: get(0),
                installed: (get(1) == "installed").then(|| get(3)),
                version: dash(get(2)),
                kind: get(4),
                summary: dash(get(5)),
                category: dash(get(6)),
                featured: get(7) == "1",
            }
        })
        .filter(|it| !it.name.is_empty())
        .collect()
}

pub fn parse_updates(tsv: &str) -> Vec<Update> {
    tsv.lines()
        .filter_map(|l| {
            let f: Vec<&str> = l.split('\t').collect();
            (f.len() >= 3 && !f[0].is_empty()).then(|| Update {
                name: f[0].to_string(),
                have: f[1].to_string(),
                new: f[2].to_string(),
                note: f.get(3).copied().unwrap_or("").to_string(),
            })
        })
        .collect()
}

/// `tlstore info --tsv <name>`: key/value lines.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Info {
    fields: Vec<(String, String)>,
}

impl Info {
    pub fn parse(tsv: &str) -> Info {
        Info {
            fields: tsv
                .lines()
                .filter_map(|l| l.split_once('\t'))
                .map(|(k, v)| (k.to_string(), v.to_string()))
                .collect(),
        }
    }
    /// The value for `key`, None when missing, empty or `-`.
    pub fn get(&self, key: &str) -> Option<&str> {
        self.fields
            .iter()
            .find(|(k, _)| k == key)
            .map(|(_, v)| v.as_str())
            .filter(|v| !v.is_empty() && *v != "-")
    }
    pub fn setup(&self) -> bool {
        self.get("Setup") == Some("1")
    }
    /// `owner/repo`, never for a setup.
    pub fn upstream(&self) -> Option<&str> {
        if self.setup() {
            None
        } else {
            self.get("Upstream").filter(|u| u.contains('/'))
        }
    }
    /// The one-line standfirst (the summary when the catalog has none).
    pub fn standfirst(&self) -> &str {
        self.get("Standfirst").or(self.get("Summary")).unwrap_or("")
    }
    /// README section titles the catalog asks to leave out (`Readme-skip`, `|`-separated).
    pub fn readme_skip(&self) -> Vec<String> {
        self.get("Readme-skip")
            .map(|n| n.split('|').map(str::trim).filter(|s| !s.is_empty()).map(str::to_string).collect())
            .unwrap_or_default()
    }
}

/// Everything the screens read, loaded through the script.
#[derive(Default)]
pub struct Catalog {
    pub items: Vec<Item>,
    pub updates: Vec<Update>,
    infos: HashMap<String, Info>,
    /// Set when the last load failed (no catalog yet, script missing).
    pub error: Option<String>,
}

impl Catalog {
    /// `list --tsv` plus the offline update check.
    pub fn load(env: &Env) -> Catalog {
        let mut c = Catalog::default();
        c.reload(env);
        c
    }

    pub fn reload(&mut self, env: &Env) {
        match env.script(&["list", "--tsv"]) {
            Ok(out) => {
                self.items = parse_list(&out);
                self.error = None;
            }
            Err(_) => {
                self.items.clear();
                self.error = Some("The list could not be read. Check your connection and try again.".into());
            }
        }
        if let Ok(out) = env.script(&["update", "--check", "--tsv", "--offline"]) {
            self.updates = parse_updates(&out);
        }
        self.infos.clear();
    }

    pub fn item(&self, name: &str) -> Option<&Item> {
        self.items.iter().find(|i| i.name == name)
    }

    pub fn update_for(&self, name: &str) -> Option<&Update> {
        self.updates.iter().find(|u| u.name == name)
    }

    pub fn status(&self, it: &Item) -> Status {
        if let Some(u) = self.update_for(&it.name) {
            return Status::Update(u.new.clone());
        }
        match (&it.installed, it.featured) {
            (Some(_), _) => Status::Installed,
            (None, true) => Status::New,
            (None, false) => Status::None,
        }
    }

    /// `info --tsv <name>`, cached until the next reload.
    pub fn info(&mut self, env: &Env, name: &str) -> &Info {
        self.infos.entry(name.to_string()).or_insert_with(|| {
            env.script(&["info", "--tsv", name]).map(|o| Info::parse(&o)).unwrap_or_default()
        })
    }
}

/// The four step words every item goes through, in order.
pub const STEPS: [&str; 4] = ["fetched", "signature checked", "putting files in place", "ready"];

/// The word a Front row shows while its step is in progress (`step` indexes [`STEPS`];
/// 4 = all done).
pub fn step_word(step: usize) -> &'static str {
    match step {
        0 => "fetching…",
        1 => "checking…",
        2 => "placing…",
        _ => "ready",
    }
}

/// One line of the `--progress` stream.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Progress {
    Step { name: String, pct: u8, words: String },
    Done { name: String, ok: bool, message: String },
}

pub fn parse_progress(line: &str) -> Option<Progress> {
    let f: Vec<&str> = line.split('\t').collect();
    match *f.first()? {
        "step" if f.len() >= 3 => Some(Progress::Step {
            name: f[1].to_string(),
            pct: f[2].trim().parse::<u32>().ok()?.min(100) as u8,
            words: f.get(3).copied().unwrap_or("").to_string(),
        }),
        "done" if f.len() >= 3 => Some(Progress::Done {
            name: f[1].to_string(),
            ok: f[2] == "ok",
            message: f.get(3).copied().unwrap_or("").to_string(),
        }),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn list_rows_parse() {
        let tsv = "dawn\tavailable\t0.1.3\t\tbinary\ta quiet place\tNote taking\t1\n\
                   kitten\tinstalled\t0.49\t0.48.2\tbinary\tpictures\tTools\t0\n";
        let v = parse_list(tsv);
        assert_eq!(v.len(), 2);
        assert_eq!(v[0].no, 1);
        assert!(v[0].featured && v[0].installed.is_none());
        assert_eq!(v[1].installed.as_deref(), Some("0.48.2"));
        assert_eq!(v[1].category, "Tools");
    }

    #[test]
    fn info_and_updates_parse() {
        let i = Info::parse(
            "Kind\tbinary\nUpstream\tkovidgoyal/kitty\nSetup\t0\nSummary\tsum\nReadme-skip\tPortability | Faq\n",
        );
        assert_eq!(i.upstream(), Some("kovidgoyal/kitty"));
        assert_eq!(i.readme_skip(), vec!["Portability", "Faq"]);
        assert_eq!(i.standfirst(), "sum");
        assert!(Info::parse("Setup\t1\nUpstream\tfish/fish\n").upstream().is_none());
        assert!(Info::parse("Readme-skip\t-\n").readme_skip().is_empty());
        let u = parse_updates("kitten\t0.48.2\t0.49\t\nclaude-code\t1.0\tlatest\tlatest\n");
        assert_eq!(u[1].new, "latest");
    }

    #[test]
    fn progress_lines_parse() {
        assert_eq!(
            parse_progress("step\tkitten\t60\tsignature checked"),
            Some(Progress::Step { name: "kitten".into(), pct: 60, words: "signature checked".into() })
        );
        assert_eq!(
            parse_progress("done\tkitten\tok\tkept your config.fish"),
            Some(Progress::Done { name: "kitten".into(), ok: true, message: "kept your config.fish".into() })
        );
        assert_eq!(parse_progress("Installing: kitten"), None);
        assert_eq!(step_word(0), "fetching…");
        assert_eq!(step_word(2), "placing…");
        assert_eq!(step_word(4), "ready");
    }
}
