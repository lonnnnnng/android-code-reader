interface SourceFile {
  path: string;
  readOnly: boolean;
}

const current: SourceFile = { path: "Main.kt", readOnly: true };
console.log(current.path);
