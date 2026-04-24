package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.util.ArrayList;
import org.json.JSONObject;

public class AnalysisRequestBuilder {

  public static JSONObject buildRequest(
      String id,
      BoardHistoryNode analyzeNode,
      int maxVisits,
      boolean includePVVisits,
      boolean includeOwnership,
      boolean includeMovesOwnership) {
    JSONObject request = new JSONObject();
    request.put("id", id);
    request.put("maxVisits", maxVisits);
    request.put("includePVVisits", includePVVisits);
    request.put("includeOwnership", includeOwnership);
    request.put("includeMovesOwnership", includeMovesOwnership);
    BoardHistoryNode snapshotAnchor = AnalysisEngine.findSnapshotAnchor(analyzeNode);
    BoardHistoryNode initialStateAnchor = AnalysisEngine.resolveInitialStateAnchor(snapshotAnchor);
    ArrayList<String[]> initialStoneList = AnalysisEngine.collectInitialStones(initialStateAnchor);
    if (!initialStoneList.isEmpty()) {
      request.put("initialStones", initialStoneList);
    }
    String initialPlayer = AnalysisEngine.collectInitialPlayer(initialStateAnchor);
    if (initialPlayer != null) {
      request.put("initialPlayer", initialPlayer);
    }
    addRules(request);
    request.put("komi", Lizzie.board.getHistory().getGameInfo().getKomi());
    request.put("boardXSize", Board.boardWidth);
    request.put("boardYSize", Board.boardHeight);
    ArrayList<String[]> moveList =
        AnalysisEngine.collectHistoryActions(analyzeNode, snapshotAnchor);
    ArrayList<Integer> moveTurns = new ArrayList<Integer>();
    moveTurns.add(moveList.size());
    request.put("moves", moveList);
    request.put("analyzeTurns", moveTurns);
    JSONObject overrideSettings = new JSONObject();
    overrideSettings.put("reportAnalysisWinratesAs", "SIDETOMOVE");
    request.put("overrideSettings", overrideSettings);
    return request;
  }

  static void addRules(JSONObject request) {
    JSONObject ruleSettings;
    if (!Lizzie.config.analysisUseCurrentRules) {
      if (!Lizzie.config.analysisSpecificRules.equals("")) {
        ruleSettings = new JSONObject(Lizzie.config.analysisSpecificRules);
        request.put("rules", ruleSettings);
      } else request.put("rules", "tromp-taylor");
    } else if (!Lizzie.config.currentKataGoRules.equals("")) {
      ruleSettings = new JSONObject(new String(Lizzie.config.currentKataGoRules.substring(2)));
      request.put("rules", ruleSettings);
    } else if (Lizzie.config.autoLoadKataRules && !Lizzie.config.kataRules.equals("")) {
      ruleSettings = new JSONObject(Lizzie.config.kataRules);
      request.put("rules", ruleSettings);
    } else request.put("rules", "tromp-taylor");
  }
}
