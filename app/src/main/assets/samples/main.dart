class ReaderState {
  const ReaderState(this.fileName, {this.readOnly = true});

  final String fileName;
  final bool readOnly;
}
