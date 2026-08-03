/*
  Pobiera ikony Material Symbols Rounded z fonts.google.com/icons i wypisuje
  z nich plik core/.../design/icon/KajetIcons.kt.

  Dlaczego tak, a nie biblioteką: material-icons-extended jest zamrożone na
  starym zestawie Material Icons, a strona Kajetu rysuje Material Symbols.
  Ściągając te same rysunki, aplikacja i strona pokazują jedną ikonę, a projekt
  nie zyskuje ani jednej nowej zależności — ścieżki czyta PathParser, który
  siedzi już w compose-ui-graphics.

  Użycie:  node tools/ikony.mjs
  Potrzebny internet. Plik nadpisuje się w całości.
*/

import { writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const TARGET = path.join(
  HERE,
  "..",
  "core",
  "src",
  "main",
  "java",
  "wojtoteka",
  "ovh",
  "kajet",
  "core",
  "design",
  "icon",
  "KajetIcons.kt",
);

/*
  Nazwa w Kajecie  ->  nazwa ikony na fonts.google.com/icons.
  Kolejność i podział na grupy jak w dotychczasowym pliku, żeby dało się
  porównać jedno z drugim.
*/
const ICONS = {
  // Biblioteka i pliki
  Library: "local_library",
  Folder: "folder",
  FolderOpen: "folder_open",
  HandwrittenNote: "draw",
  TextNote: "article",
  MindMapIcon: "account_tree",
  CodeFile: "code",
  Favourites: "star",
  Recent: "history",
  Tag: "label",
  Bin: "delete",
  Search: "search",
  Plus: "add",
  MoreDots: "more_vert",
  ArrowRight: "chevron_right",
  ArrowDown: "expand_more",
  BackArrow: "arrow_back",
  SettingsCog: "tune",

  // Narzędzia do pisania
  Pen: "stylus",
  Highlighter: "ink_highlighter",
  Eraser: "ink_eraser",
  EraserStroke: "ink_eraser_off",
  Lasso: "highlight_alt",
  Ruler: "straighten",
  Undo: "undo",
  Redo: "redo",
  TextBox: "text_fields",
  PhotoFrame: "image",
  CameraBody: "photo_camera",
  DrawingPad: "gesture",
  ColorSwatch: "palette",
  PageRuling: "grid_on",
  AddPage: "note_add",
  FingerDraws: "touch_app",
  FingerScrolls: "swipe_vertical",
  RecogniseText: "spellcheck",

  // Eksport i udostępnianie
  Export: "download",
  ShareArrow: "share",
  Printer: "print",
  Close: "close",
  Confirm: "check",

  // Kod
  PlayRun: "play_arrow",
  StopSquare: "stop",
  OutputPanel: "terminal",
  ErrorMark: "error",
  InputArrow: "input",
  WordWrap: "wrap_text",
  Offline: "cloud_off",

  // Działania na plikach
  Move: "drive_file_move",
  Copy: "content_copy",
  Restore: "restore",
  FitToView: "fit_screen",
  NodeDot: "add_box",

  // Ikony do wyboru przy folderze przedmiotu
  Letters: "abc",
  Operations: "calculate",
  MusicNote: "music_note",
  Flask: "science",
  Globe: "public",
  BrushTip: "brush",
  Heart: "favorite",
  Atom: "biotech",
  Dna: "genetics",
  MapPin: "map",
  Cog: "settings",
  Bulb: "lightbulb",
  Compass: "explore",
  Rocket: "rocket_launch",
  Crown: "workspace_premium",
  Cup: "local_cafe",
  Tree: "park",
  Mountain: "landscape",
  CloudMark: "cloud",
  KeyShape: "key",
  Clock: "schedule",
  Calendar: "calendar_month",
  Flag: "flag",
  Microscope: "biotech",
  Ball: "sports_soccer",
  Mask: "theater_comedy",
  Scales: "balance",
  Shield: "shield",
  House: "home",

  // Formatowanie tekstu
  Bold: "format_bold",
  Italic: "format_italic",
  Underline: "format_underlined",
  Strikethrough: "format_strikethrough",
  TextColour: "format_color_text",
  Highlight: "format_color_fill",
  TextSize: "format_size",
  DividerLine: "horizontal_rule",
  AlignLeft: "format_align_left",
  AlignCentre: "format_align_center",
  AlignRight: "format_align_right",
  BulletList: "format_list_bulleted",
  NumberedList: "format_list_numbered",
  TaskList: "checklist",
  TableGrid: "table_chart",
  HeadingMark: "title",
  Quote: "format_quote",
  LinkChain: "link",
  Opacity: "opacity",
  Thickness: "line_weight",
  Fineliner: "edit",
  Pencil: "stylus_note",
  DashedLine: "line_style",
  Connect: "hub",

  // Stan zapisu i konto
  Saved: "check_circle",
  CloudDone: "cloud_done",
  Account: "account_circle",
};

/** Ikony wybierane przy folderze: identyfikator zapisany w pliku -> nazwa w Kajecie. */
const FOLDER_ICONS = [
  ["folder", "Folder"],
  ["ksiazki", "Library"],
  ["litery", "Letters"],
  ["dzialania", "Operations"],
  ["nuta", "MusicNote"],
  ["kolba", "Flask"],
  ["globus", "Globe"],
  ["kod", "CodeFile"],
  ["gwiazdka", "Favourites"],
  ["pedzel", "BrushTip"],
  ["serce", "Heart"],
  ["atom", "Atom"],
  ["dna", "Dna"],
  ["mapa", "MapPin"],
  ["zebatka", "Cog"],
  ["zarowka", "Bulb"],
  ["kompas", "Compass"],
  ["rakieta", "Rocket"],
  ["korona", "Crown"],
  ["filizanka", "Cup"],
  ["drzewo", "Tree"],
  ["gora", "Mountain"],
  ["cloud", "CloudMark"],
  ["klucz", "KeyShape"],
  ["zegar", "Clock"],
  ["kalendarz", "Calendar"],
  ["flaga", "Flag"],
  ["mikroskop", "Microscope"],
  ["pilka", "Ball"],
  ["maska", "Mask"],
  ["waga", "Scales"],
  ["tarcza", "Shield"],
  ["dom", "House"],
  ["aparat", "CameraBody"],
  ["zdjecie", "PhotoFrame"],
  ["rysunek", "DrawingPad"],
  ["wezel", "NodeDot"],
  ["tag", "Tag"],
];

const address = (name) =>
  `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/${name}/default/24px.svg`;

async function fetchPath(name) {
  const response = await fetch(address(name), { signal: AbortSignal.timeout(20_000) });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const svg = await response.text();

  // Rysunki Material Symbols to jedna albo kilka ścieżek w układzie
  // 0 -960 960 960. Sklejamy je w jeden ciąg — reguła wypełniania jest ta sama.
  const parts = [...svg.matchAll(/<path[^>]*\sd="([^"]+)"/g)].map((hit) => hit[1]);
  if (parts.length === 0) throw new Error("brak ścieżki w pliku SVG");
  return parts.join(" ");
}

const missing = [];
const drawings = new Map();
const wanted = [...new Set(Object.values(ICONS))];

console.log(`Pobieram ${wanted.length} ikon z fonts.google.com/icons…\n`);

// Po kilka naraz, żeby nie zasypać serwera Google.
for (let i = 0; i < wanted.length; i += 8) {
  const batch = wanted.slice(i, i + 8);
  const results = await Promise.all(
    batch.map(async (name) => {
      try {
        return [name, await fetchPath(name)];
      } catch (problem) {
        missing.push(`${name} — ${problem.message}`);
        return [name, null];
      }
    }),
  );
  for (const [name, drawing] of results) {
    if (drawing) drawings.set(name, drawing);
  }
  process.stdout.write(`  ${Math.min(i + 8, wanted.length)}/${wanted.length}\r`);
}

console.log("");

if (missing.length > 0) {
  console.error(`\nNie udało się pobrać ${missing.length}:`);
  for (const entry of missing) console.error(`  ${entry}`);
  console.error("\nPopraw nazwy w tools/ikony.mjs i uruchom jeszcze raz.");
  process.exit(1);
}

const body = Object.entries(ICONS)
  .map(
    ([kajet, symbol]) =>
      `    /** ${symbol} */\n    val ${kajet} by lazy { icon("${symbol}", "${drawings.get(symbol)}") }`,
  )
  .join("\n\n");

const folders = FOLDER_ICONS.map(([id, name]) => `            "${id}" to ${name},`).join("\n");

const file = `package wojtoteka.ovh.kajet.core.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
  Ikony Material Symbols Rounded z fonts.google.com/icons.

  Plik jest wypisywany przez tools/ikony.mjs — nie poprawiaj go ręcznie.
  Żeby dołożyć albo zamienić ikonę, dopisz ją w tamtym spisie i uruchom:

      node tools/ikony.mjs

  Rysunki Google są w układzie 960 na 960, liczonym od góry w górę (y od -960
  do 0). Dlatego każdy leży w grupie przesuniętej o 960 w dół — dzięki temu
  ImageVector ma zwyczajny układ od lewego górnego rogu, a Icon() rysuje ikonę
  w kolorze treści, tak jak wszystkie pozostałe.
*/
object KajetIcons {

${body}

    val folderIcons: List<Pair<String, ImageVector>> by lazy {
        listOf(
${folders}
        )
    }

    fun folderIcon(id: String?): ImageVector =
        folderIcons.firstOrNull { it.first == id }?.second ?: Folder
}

private const val VIEWPORT = 960f

private fun icon(name: String, drawing: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    )
        .addGroup(name = name, translationY = VIEWPORT)
        .addPath(
            pathData = PathParser().parsePathString(drawing).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .clearGroup()
        .build()
`;

await writeFile(TARGET, file, "utf8");
console.log(`\nGotowe: ${Object.keys(ICONS).length} ikon w ${path.relative(process.cwd(), TARGET)}`);
