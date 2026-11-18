package ro.atf.chess;

import java.util.Map;
import java.util.Set;

/** A promotion may defeat the stability veto only after its capture/recapture is closed. */
record PromotionExchangePolicy(boolean enabled,int minimumMaterialGainPawns) {
    static final PromotionExchangePolicy CURRENT=read(ChessYaml.resource("hard-promotion-exchange.yaml"));

    static PromotionExchangePolicy read(Map<?,?> values) {
        if(!values.keySet().equals(Set.of("schema","enabled","minimum-material-gain-pawns"))
                ||ChessYaml.number(values,"schema")!=1||!(values.get("enabled") instanceof Boolean enabled))
            throw new IllegalArgumentException("Invalid promotion exchange policy");
        int minimum=ChessYaml.number(values,"minimum-material-gain-pawns");
        if(minimum<1||minimum>8)throw new IllegalArgumentException("Invalid closed promotion gain");
        return new PromotionExchangePolicy(enabled,minimum);
    }

    boolean matches(ChessPosition before,TacticalAnalysis.Variation variation) {
        if(!enabled||variation.ending()!=TacticalAnalysis.Ending.HORIZON||variation.line().size()<3)return false;
        var moves=variation.line();var promotion=moves.get(0);
        if(promotion.promotion()==null||moves.get(1).to()!=promotion.to()||moves.get(2).to()!=promotion.to())return false;
        var promoted=before.play(promotion);var victim=MatchMaterial.victim(promoted,moves.get(1));
        if(victim==null||victim.side()!=before.turn()||victim.kind()!=promotion.promotion())return false;
        var captured=promoted.play(moves.get(1));var capturer=MatchMaterial.victim(captured,moves.get(2));
        if(capturer==null||capturer.side()==before.turn())return false;
        var closed=captured.play(moves.get(2));int original=MatchMaterial.balance(before,before.turn());
        if(MatchMaterial.balance(closed,before.turn())-original<minimumMaterialGainPawns)return false;
        var end=closed;for(var move:moves.subList(3,moves.size()))end=end.play(move);
        return MatchMaterial.balance(end,before.turn())-original>=minimumMaterialGainPawns;
    }
}
