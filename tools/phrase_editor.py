#!/usr/bin/env python3
"""A small GUI for editing the phrase and pattern JSON under `content/`.

The app reads its phrase library and scale-pattern library from Kotlin, generated from these JSON
files by `tools/generate_phrase_library.py` - see that script for why. This editor is the everyday
way to change them: a list of phrases (or patterns) on the left, and on the right, everything about
whichever one is selected - its name and description, how many bars make one practice unit, and a
bar-by-bar list of its notes, each with its own pitch (or scale degree, for a pattern), length and
dots.

Stdlib only - tkinter, nothing to install. Run it from anywhere:

    python tools/phrase_editor.py

Saving writes straight to the JSON file; it does not touch the app's Kotlin. Press "Regenerate app
content" (or run the generator yourself) to turn saved changes into what the app actually ships -
exactly as saving an SVG does not update an icon until `tools/svg_to_vector.py` is run.
"""
from __future__ import annotations

import copy
import json
import pathlib
import subprocess
import sys
import tkinter as tk
from tkinter import messagebox, simpledialog, ttk

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import generate_phrase_library as gen  # noqa: E402  (needs the sys.path tweak above)

ROOT = pathlib.Path(__file__).resolve().parent.parent
PHRASES_DIR = ROOT / "content" / "phrases"
PATTERNS_DIR = ROOT / "content" / "patterns"

DURATIONS = ["whole", "half", "quarter", "eighth", "sixteenth"]
DOT_CHOICES = [0, 1, 2]


def default_doc(content_id: str, is_phrase: bool) -> dict:
    # One whole note fills a default 4/4 bar exactly, so a brand-new item validates and saves as it
    # is - nothing here is left half a bar short, waiting to fail the first time it is saved.
    doc = {
        "id": content_id,
        "name": content_id.replace("_", " ").title(),
        "description": "",
        "trainingBars": 1,
        "timeSignature": None,
        "bars": [{"steps": [{"duration": "whole", **({"note": "C4"} if is_phrase else {"degree": 1})}]}],
    }
    if is_phrase:
        doc["accidentalStyle"] = None
    return doc


class ContentTab(ttk.Frame):
    """One tab of the editor: the phrase library, or the pattern library - same shape, one field
    of a step apart (a phrase's step names a note, a pattern's a scale degree)."""

    def __init__(self, master: tk.Misc, directory: pathlib.Path, is_phrase: bool, status: tk.StringVar):
        super().__init__(master)
        self.directory = directory
        self.is_phrase = is_phrase
        self.status = status
        self.pitch_label = "Note (e.g. C4, F#3, Bb2)" if is_phrase else "Degree (1, 2, 3, ...)"

        self.current_path: pathlib.Path | None = None
        self.current_doc: dict | None = None
        self.dirty = False
        # Path -> node id, populated as files load; lets a rename find its old tree row.
        self._suspend_trace = False

        self._build()
        self.reload_file_list()

    # -- layout ---------------------------------------------------------------------------------

    def _build(self) -> None:
        outer = ttk.Panedwindow(self, orient=tk.HORIZONTAL)
        outer.pack(fill=tk.BOTH, expand=True)

        left = ttk.Frame(outer, padding=4)
        outer.add(left, weight=1)

        button_row = ttk.Frame(left)
        button_row.pack(fill=tk.X, pady=(0, 4))
        ttk.Button(button_row, text="New...", command=self._new_item).pack(side=tk.LEFT)
        ttk.Button(button_row, text="Delete", command=self._delete_item).pack(side=tk.LEFT, padx=(4, 0))

        self.file_list = tk.Listbox(left, exportselection=False, width=28)
        self.file_list.pack(fill=tk.BOTH, expand=True)
        self.file_list.bind("<<ListboxSelect>>", self._on_select_file)

        right = ttk.Frame(outer, padding=4)
        outer.add(right, weight=3)
        self._build_metadata(right)
        self._build_step_editor(right)

        save_row = ttk.Frame(right)
        save_row.pack(fill=tk.X, pady=(6, 0))
        ttk.Button(save_row, text="Save", command=self._save).pack(side=tk.LEFT)

    def _build_metadata(self, parent: tk.Misc) -> None:
        box = ttk.LabelFrame(parent, text="About this " + ("phrase" if self.is_phrase else "pattern"))
        box.pack(fill=tk.X, pady=(0, 6))

        self.var_id = tk.StringVar()
        self.var_name = tk.StringVar()
        self.var_description = tk.StringVar()
        self.var_training_bars = tk.IntVar(value=1)
        self.var_beats_per_bar = tk.IntVar(value=4)
        self.var_beat_unit = tk.IntVar(value=4)
        self.var_accidental = tk.StringVar(value="sharps")

        row = 0
        ttk.Label(box, text="Id").grid(row=row, column=0, sticky="w")
        ttk.Entry(box, textvariable=self.var_id, width=24).grid(row=row, column=1, sticky="w", padx=4)
        ttk.Label(box, text="(letters, digits, underscore - this is the filename)").grid(
            row=row, column=2, columnspan=2, sticky="w",
        )
        row += 1

        ttk.Label(box, text="Name").grid(row=row, column=0, sticky="w")
        ttk.Entry(box, textvariable=self.var_name, width=32).grid(
            row=row, column=1, columnspan=3, sticky="we", padx=4,
        )
        row += 1

        ttk.Label(box, text="Description").grid(row=row, column=0, sticky="w")
        ttk.Entry(box, textvariable=self.var_description, width=60).grid(
            row=row, column=1, columnspan=3, sticky="we", padx=4,
        )
        row += 1

        ttk.Label(box, text="Training bars").grid(row=row, column=0, sticky="w")
        ttk.Spinbox(box, from_=1, to=16, textvariable=self.var_training_bars, width=4).grid(
            row=row, column=1, sticky="w", padx=4,
        )
        ttk.Label(box, text="how many bars make one practice unit").grid(
            row=row, column=2, columnspan=2, sticky="w",
        )
        row += 1

        ttk.Label(box, text="Time signature").grid(row=row, column=0, sticky="w")
        sig = ttk.Frame(box)
        sig.grid(row=row, column=1, sticky="w", padx=4)
        ttk.Spinbox(sig, from_=1, to=16, textvariable=self.var_beats_per_bar, width=3).pack(side=tk.LEFT)
        ttk.Label(sig, text="/").pack(side=tk.LEFT)
        ttk.Spinbox(sig, from_=1, to=16, textvariable=self.var_beat_unit, width=3).pack(side=tk.LEFT)
        row += 1

        if self.is_phrase:
            ttk.Label(box, text="Accidentals").grid(row=row, column=0, sticky="w")
            accidentals = ttk.Frame(box)
            accidentals.grid(row=row, column=1, columnspan=2, sticky="w", padx=4)
            ttk.Radiobutton(accidentals, text="Sharps", value="sharps", variable=self.var_accidental).pack(
                side=tk.LEFT,
            )
            ttk.Radiobutton(accidentals, text="Flats", value="flats", variable=self.var_accidental).pack(
                side=tk.LEFT,
            )
            row += 1

        for var in (
            self.var_id, self.var_name, self.var_description, self.var_training_bars,
            self.var_beats_per_bar, self.var_beat_unit, self.var_accidental,
        ):
            var.trace_add("write", self._mark_dirty)

        for col in range(4):
            box.columnconfigure(col, weight=1 if col == 3 else 0)

    def _build_step_editor(self, parent: tk.Misc) -> None:
        box = ttk.LabelFrame(parent, text="Bars and notes")
        box.pack(fill=tk.BOTH, expand=True)

        panes = ttk.Panedwindow(box, orient=tk.HORIZONTAL)
        panes.pack(fill=tk.BOTH, expand=True, padx=4, pady=4)

        tree_frame = ttk.Frame(panes)
        panes.add(tree_frame, weight=2)

        columns = ("pitch", "duration")
        self.tree = ttk.Treeview(tree_frame, columns=columns, show="tree headings", selectmode="browse")
        self.tree.heading("#0", text="Bar")
        self.tree.heading("pitch", text="Note" if self.is_phrase else "Degree")
        self.tree.heading("duration", text="Length")
        self.tree.column("#0", width=90, stretch=False)
        self.tree.column("pitch", width=110, stretch=False)
        self.tree.column("duration", width=90, stretch=False)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.tree.bind("<<TreeviewSelect>>", self._on_select_step)

        tree_scroll = ttk.Scrollbar(tree_frame, orient=tk.VERTICAL, command=self.tree.yview)
        tree_scroll.pack(side=tk.LEFT, fill=tk.Y)
        self.tree.configure(yscrollcommand=tree_scroll.set)

        tree_buttons = ttk.Frame(box)
        tree_buttons.pack(fill=tk.X, padx=4)
        ttk.Button(tree_buttons, text="Add bar", command=self._add_bar).pack(side=tk.LEFT)
        ttk.Button(tree_buttons, text="Delete bar", command=self._delete_bar).pack(side=tk.LEFT, padx=(4, 0))
        ttk.Button(tree_buttons, text="Move bar up", command=lambda: self._move_bar(-1)).pack(
            side=tk.LEFT, padx=(12, 0),
        )
        ttk.Button(tree_buttons, text="Move bar down", command=lambda: self._move_bar(1)).pack(
            side=tk.LEFT, padx=(4, 0),
        )

        editor = ttk.Frame(panes, padding=(8, 0))
        panes.add(editor, weight=1)

        self.var_rest = tk.BooleanVar(value=False)
        self.var_pitch = tk.StringVar()
        self.var_duration = tk.StringVar(value="quarter")
        self.var_dots = tk.IntVar(value=0)

        ttk.Label(editor, text="Selected note").pack(anchor="w")
        ttk.Checkbutton(editor, text="Rest (no pitch)", variable=self.var_rest, command=self._on_rest_toggle).pack(
            anchor="w", pady=(4, 0),
        )
        ttk.Label(editor, text=self.pitch_label).pack(anchor="w", pady=(8, 0))
        self.pitch_entry = ttk.Entry(editor, textvariable=self.var_pitch, width=14)
        self.pitch_entry.pack(anchor="w")

        ttk.Label(editor, text="Length").pack(anchor="w", pady=(8, 0))
        ttk.Combobox(
            editor, textvariable=self.var_duration, values=DURATIONS, state="readonly", width=12,
        ).pack(anchor="w")

        ttk.Label(editor, text="Dots").pack(anchor="w", pady=(8, 0))
        ttk.Combobox(
            editor, textvariable=self.var_dots, values=DOT_CHOICES, state="readonly", width=4,
        ).pack(anchor="w")

        ttk.Button(editor, text="Apply to selected note", command=self._apply_step).pack(anchor="w", pady=(12, 0))

        step_buttons = ttk.Frame(editor)
        step_buttons.pack(anchor="w", pady=(16, 0))
        ttk.Button(step_buttons, text="Add note", command=self._add_step).pack(side=tk.LEFT)
        ttk.Button(step_buttons, text="Delete note", command=self._delete_step).pack(side=tk.LEFT, padx=(4, 0))
        move_buttons = ttk.Frame(editor)
        move_buttons.pack(anchor="w", pady=(4, 0))
        ttk.Button(move_buttons, text="Move up", command=lambda: self._move_step(-1)).pack(side=tk.LEFT)
        ttk.Button(move_buttons, text="Move down", command=lambda: self._move_step(1)).pack(
            side=tk.LEFT, padx=(4, 0),
        )

    # -- file list --------------------------------------------------------------------------------

    def reload_file_list(self, select_id: str | None = None) -> None:
        self.file_list.delete(0, tk.END)
        self._files = sorted(self.directory.glob("*.json")) if self.directory.is_dir() else []
        for path in self._files:
            doc = self._safe_load(path)
            label = f"{doc.get('id', path.stem)} - {doc.get('name', '')}" if doc else f"{path.stem} (unreadable)"
            self.file_list.insert(tk.END, label)
        if select_id:
            for index, path in enumerate(self._files):
                if path.stem == select_id:
                    # selection_set alone does not fire <<ListboxSelect>> - only real user
                    # interaction does - so the load has to be triggered directly.
                    self.file_list.selection_clear(0, tk.END)
                    self.file_list.selection_set(index)
                    self.file_list.activate(index)
                    self._on_select_file()
                    break

    @staticmethod
    def _safe_load(path: pathlib.Path) -> dict | None:
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None

    def _on_select_file(self, _event=None) -> None:
        selection = self.file_list.curselection()
        if not selection:
            return
        if not self._confirm_discard_if_dirty():
            self._reselect_current()
            return
        path = self._files[selection[0]]
        doc = self._safe_load(path)
        if doc is None:
            messagebox.showerror("Cannot open", f"{path} is not valid JSON.")
            return
        self.current_path = path
        self.current_doc = doc
        self._load_into_form(doc)
        self.dirty = False
        self.status.set(f"Opened {path.relative_to(ROOT)}")

    def _reselect_current(self) -> None:
        if self.current_path is None:
            return
        for index, path in enumerate(self._files):
            if path == self.current_path:
                self.file_list.selection_clear(0, tk.END)
                self.file_list.selection_set(index)
                return

    def _confirm_discard_if_dirty(self) -> bool:
        if not self.dirty:
            return True
        return messagebox.askyesno("Unsaved changes", "Discard the unsaved changes to the current item?")

    # -- loading / reading the form -----------------------------------------------------------

    def _load_into_form(self, doc: dict) -> None:
        self._suspend_trace = True
        self.var_id.set(doc.get("id", ""))
        self.var_name.set(doc.get("name", ""))
        self.var_description.set(doc.get("description", ""))
        self.var_training_bars.set(doc.get("trainingBars", 1))
        beats, unit = gen.time_signature_of(doc.get("timeSignature"))
        self.var_beats_per_bar.set(beats)
        self.var_beat_unit.set(unit)
        if self.is_phrase:
            self.var_accidental.set(doc.get("accidentalStyle") or "sharps")
        self._suspend_trace = False

        self.tree.delete(*self.tree.get_children())
        for bar_index, bar in enumerate(doc.get("bars", []), start=1):
            bar_node = self.tree.insert("", tk.END, text=f"Bar {bar_index}", open=True, tags=("bar",))
            for step in bar.get("steps", []):
                self._insert_step_node(bar_node, step)
        self._clear_step_form()

    def _insert_step_node(self, bar_node: str, step: dict) -> None:
        pitch = step.get("note") if self.is_phrase else step.get("degree")
        pitch_text = "" if pitch is None else str(pitch)
        label = "rest" if pitch is None else pitch_text
        self.tree.insert(bar_node, tk.END, text="", values=(label, step.get("duration", "quarter")), tags=("step",))

    # -- editing the tree ----------------------------------------------------------------------

    def _selected_bar_node(self) -> str | None:
        selection = self.tree.selection()
        if not selection:
            return None
        node = selection[0]
        return node if "bar" in self.tree.item(node, "tags") else self.tree.parent(node)

    def _add_bar(self) -> None:
        if self.current_doc is None:
            return
        bar_node = self.tree.insert("", tk.END, text=f"Bar {len(self.tree.get_children()) + 1}", open=True, tags=("bar",))
        default_step = {"duration": "quarter"}
        default_step["note" if self.is_phrase else "degree"] = "C4" if self.is_phrase else 1
        self._insert_step_node(bar_node, default_step)
        self._renumber_bars()
        self._mark_dirty()

    def _delete_bar(self) -> None:
        bar_node = self._selected_bar_node()
        if bar_node is None:
            messagebox.showinfo("Delete bar", "Select a bar (or a note in it) first.")
            return
        if len(self.tree.get_children()) <= 1:
            messagebox.showinfo("Delete bar", "A phrase or pattern needs at least one bar.")
            return
        self.tree.delete(bar_node)
        self._renumber_bars()
        self._clear_step_form()
        self._mark_dirty()

    def _move_bar(self, direction: int) -> None:
        bar_node = self._selected_bar_node()
        if bar_node is None:
            return
        siblings = self.tree.get_children("")
        index = siblings.index(bar_node)
        new_index = index + direction
        if 0 <= new_index < len(siblings):
            self.tree.move(bar_node, "", new_index)
            self._renumber_bars()
            self._mark_dirty()

    def _renumber_bars(self) -> None:
        for index, bar_node in enumerate(self.tree.get_children(""), start=1):
            self.tree.item(bar_node, text=f"Bar {index}")

    def _add_step(self) -> None:
        bar_node = self._selected_bar_node()
        if bar_node is None:
            if not self.tree.get_children(""):
                self._add_bar()
                return
            messagebox.showinfo("Add note", "Select a bar (or a note in it) to add to first.")
            return
        default_step = {"duration": "quarter"}
        default_step["note" if self.is_phrase else "degree"] = "C4" if self.is_phrase else 1
        node = self._insert_step_node(bar_node, default_step)
        self._mark_dirty()

    def _delete_step(self) -> None:
        selection = self.tree.selection()
        if not selection or "step" not in self.tree.item(selection[0], "tags"):
            messagebox.showinfo("Delete note", "Select a note first.")
            return
        bar_node = self.tree.parent(selection[0])
        if len(self.tree.get_children(bar_node)) <= 1:
            messagebox.showinfo("Delete note", "A bar needs at least one note or rest.")
            return
        self.tree.delete(selection[0])
        self._clear_step_form()
        self._mark_dirty()

    def _move_step(self, direction: int) -> None:
        selection = self.tree.selection()
        if not selection or "step" not in self.tree.item(selection[0], "tags"):
            return
        node = selection[0]
        parent = self.tree.parent(node)
        siblings = self.tree.get_children(parent)
        index = siblings.index(node)
        new_index = index + direction
        if 0 <= new_index < len(siblings):
            self.tree.move(node, parent, new_index)
            self._mark_dirty()

    def _on_select_step(self, _event=None) -> None:
        selection = self.tree.selection()
        if not selection or "step" not in self.tree.item(selection[0], "tags"):
            self._clear_step_form()
            return
        pitch, duration_with_dots = self.tree.item(selection[0], "values")
        is_rest = pitch in ("", "rest")
        self.var_rest.set(is_rest)
        self.var_pitch.set("" if is_rest else str(pitch))
        duration = str(duration_with_dots)
        dots = duration.count(".")
        self.var_duration.set(duration.rstrip("."))
        self.var_dots.set(dots)
        self.pitch_entry.configure(state=("disabled" if is_rest else "normal"))

    def _on_rest_toggle(self) -> None:
        self.pitch_entry.configure(state=("disabled" if self.var_rest.get() else "normal"))

    def _clear_step_form(self) -> None:
        self.var_rest.set(False)
        self.var_pitch.set("")
        self.var_duration.set("quarter")
        self.var_dots.set(0)

    def _apply_step(self) -> None:
        selection = self.tree.selection()
        if not selection or "step" not in self.tree.item(selection[0], "tags"):
            messagebox.showinfo("Apply", "Select a note first.")
            return
        is_rest = self.var_rest.get()
        pitch_text = "" if is_rest else self.var_pitch.get().strip()
        if not is_rest and not pitch_text:
            messagebox.showerror("Apply", f"Enter a {self.pitch_label.lower()}, or tick Rest.")
            return
        duration_token = self.var_duration.get() + "." * self.var_dots.get()
        try:
            gen.beats_for(duration_token, self.var_beat_unit.get() or 4)
        except ValueError as error:
            messagebox.showerror("Apply", str(error))
            return
        if not is_rest and self.is_phrase and not gen.NOTE_PATTERN.match(pitch_text):
            messagebox.showerror("Apply", f"'{pitch_text}' is not a note like C4 or F#3.")
            return
        if not is_rest and not self.is_phrase and not pitch_text.isdigit():
            messagebox.showerror("Apply", "Degree must be a whole number, e.g. 1, 2, 3.")
            return

        label = "rest" if is_rest else pitch_text
        self.tree.item(selection[0], values=(label, duration_token))
        self._mark_dirty()

    # -- dirty tracking / new / delete --------------------------------------------------------

    def _mark_dirty(self, *_args) -> None:
        if self._suspend_trace:
            return
        self.dirty = True

    def _new_item(self) -> None:
        if not self._confirm_discard_if_dirty():
            return
        kind = "phrase" if self.is_phrase else "pattern"
        content_id = simpledialog.askstring("New " + kind, "Id (lowercase, letters/digits/underscore):", parent=self)
        if not content_id:
            return
        if not gen.ID_PATTERN.match(content_id):
            messagebox.showerror("New " + kind, "Id must be lowercase letters, digits and underscores, starting with a letter.")
            return
        path = self.directory / f"{content_id}.json"
        if path.exists():
            messagebox.showerror("New " + kind, f"{path.name} already exists.")
            return
        self.directory.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(default_doc(content_id, self.is_phrase), indent=2) + "\n", encoding="utf-8")
        self.reload_file_list(select_id=content_id)
        self.status.set(f"Created {path.relative_to(ROOT)}")

    def _delete_item(self) -> None:
        selection = self.file_list.curselection()
        if not selection:
            return
        path = self._files[selection[0]]
        if not messagebox.askyesno("Delete", f"Delete {path.name}? This cannot be undone."):
            return
        path.unlink(missing_ok=True)
        self.current_path = None
        self.current_doc = None
        self.dirty = False
        self.reload_file_list()
        self._load_into_form(default_doc("", self.is_phrase))
        self.status.set(f"Deleted {path.name}")

    # -- save -----------------------------------------------------------------------------------

    def _doc_from_form(self) -> dict:
        doc: dict = {
            "id": self.var_id.get().strip(),
            "name": self.var_name.get().strip(),
            "description": self.var_description.get().strip(),
            "trainingBars": int(self.var_training_bars.get()),
            "timeSignature": f"{self.var_beats_per_bar.get()}/{self.var_beat_unit.get()}",
        }
        if self.is_phrase:
            doc["accidentalStyle"] = self.var_accidental.get()

        bars = []
        for bar_node in self.tree.get_children(""):
            steps = []
            for step_node in self.tree.get_children(bar_node):
                pitch, duration = self.tree.item(step_node, "values")
                step: dict = {"duration": duration}
                if pitch not in ("", "rest"):
                    if self.is_phrase:
                        step["note"] = pitch
                    else:
                        step["degree"] = int(pitch)
                steps.append(step)
            bars.append({"steps": steps})
        doc["bars"] = bars
        return doc

    def _save(self) -> None:
        if self.current_path is None and self.current_doc is None:
            messagebox.showinfo("Save", "Nothing is open. Use New... first.")
            return

        doc = self._doc_from_form()
        try:
            beats_per_bar, beat_unit = gen.validate_common(doc, "this item")
            gen.validate_bars(doc["bars"], beats_per_bar, beat_unit, "this item", self.is_phrase)
        except gen.ContentError as error:
            messagebox.showerror("Cannot save", str(error))
            return

        new_path = self.directory / f"{doc['id']}.json"
        if self.current_path is not None and new_path != self.current_path and new_path.exists():
            messagebox.showerror("Cannot save", f"{new_path.name} already exists.")
            return

        self.directory.mkdir(parents=True, exist_ok=True)
        new_path.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
        if self.current_path is not None and self.current_path != new_path:
            self.current_path.unlink(missing_ok=True)

        self.current_path = new_path
        self.current_doc = doc
        self.dirty = False
        self.reload_file_list(select_id=doc["id"])
        self.status.set(f"Saved {new_path.relative_to(ROOT)}")


class Editor(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Sax Trainer - Phrase & Pattern Editor")
        self.geometry("1000x650")

        self.status_var = tk.StringVar(value="Ready.")

        notebook = ttk.Notebook(self)
        notebook.pack(fill=tk.BOTH, expand=True, padx=6, pady=(6, 0))

        self.phrases_tab = ContentTab(notebook, PHRASES_DIR, is_phrase=True, status=self.status_var)
        self.patterns_tab = ContentTab(notebook, PATTERNS_DIR, is_phrase=False, status=self.status_var)
        notebook.add(self.phrases_tab, text="Phrases")
        notebook.add(self.patterns_tab, text="Patterns")

        bottom = ttk.Frame(self)
        bottom.pack(fill=tk.X, padx=6, pady=6)
        ttk.Button(bottom, text="Regenerate app content", command=self._regenerate).pack(side=tk.LEFT)
        ttk.Label(bottom, textvariable=self.status_var).pack(side=tk.LEFT, padx=(12, 0))

    def _regenerate(self) -> None:
        result = subprocess.run(
            [sys.executable, str(pathlib.Path(__file__).with_name("generate_phrase_library.py"))],
            cwd=ROOT, capture_output=True, text=True,
        )
        output = (result.stdout + result.stderr).strip()
        if result.returncode == 0:
            self.status_var.set(output.splitlines()[-1] if output else "Regenerated.")
            messagebox.showinfo("Regenerate app content", output or "Done.")
        else:
            messagebox.showerror("Regenerate app content failed", output or "See the terminal for details.")


def main() -> int:
    PHRASES_DIR.mkdir(parents=True, exist_ok=True)
    PATTERNS_DIR.mkdir(parents=True, exist_ok=True)
    Editor().mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
