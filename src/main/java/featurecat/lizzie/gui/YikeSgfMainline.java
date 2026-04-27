package featurecat.lizzie.gui;

final class YikeSgfMainline {
  private YikeSgfMainline() {}

  static String withoutVariations(String sgf) {
    if (sgf == null || sgf.isEmpty()) return sgf;
    int start = sgf.indexOf('(');
    if (start < 0) return sgf;

    Result result = parseGameTree(sgf, start);
    return result.complete ? result.text : sgf;
  }

  private static Result parseGameTree(String sgf, int start) {
    if (start >= sgf.length() || sgf.charAt(start) != '(') {
      return Result.incomplete(start);
    }

    StringBuilder out = new StringBuilder();
    out.append('(');
    boolean inValue = false;
    boolean escaping = false;
    boolean copiedFirstChildTree = false;

    for (int i = start + 1; i < sgf.length(); ) {
      char ch = sgf.charAt(i);
      if (inValue) {
        out.append(ch);
        if (escaping) {
          escaping = false;
        } else if (ch == '\\') {
          escaping = true;
        } else if (ch == ']') {
          inValue = false;
        }
        i++;
        continue;
      }

      if (ch == '[') {
        inValue = true;
        out.append(ch);
        i++;
        continue;
      }

      if (ch == '(') {
        Result child = parseGameTree(sgf, i);
        if (!child.complete) return child;
        if (!copiedFirstChildTree) {
          out.append(withoutOuterTree(child.text));
          copiedFirstChildTree = true;
        }
        i = child.nextIndex;
        continue;
      }

      if (ch == ')') {
        out.append(ch);
        return new Result(out.toString(), i + 1, true);
      }

      out.append(ch);
      i++;
    }

    return Result.incomplete(sgf.length());
  }

  private static String withoutOuterTree(String tree) {
    return tree.length() >= 2 ? tree.substring(1, tree.length() - 1) : "";
  }

  private static final class Result {
    private final String text;
    private final int nextIndex;
    private final boolean complete;

    private Result(String text, int nextIndex, boolean complete) {
      this.text = text;
      this.nextIndex = nextIndex;
      this.complete = complete;
    }

    private static Result incomplete(int nextIndex) {
      return new Result("", nextIndex, false);
    }
  }
}
