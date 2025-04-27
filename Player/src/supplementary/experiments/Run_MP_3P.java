package supplementary.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import game.Game;
import main.StringRoutines;
import other.AI;
import other.GameLoader;
import other.context.Context;
import other.model.Model;
import other.trial.Trial;
import search.mcts.MCTS;
import search.mcts.selection.MP_PNS_UCB;

public class Run_MP_3P {
    //Map<String, String> gameFiles  = new HashMap<String, String>() {{put("LOA7x7", "Lines of Action 7x7");put("LOA8x8", "Lines of Action 8x8");put("Minishogi", "Minishogi");put("Knightthrough", "Knightthrough");put("Awari", "Awari");}};
    static String USAGE_ERR = "Usage: Run <time(ms)> <game_name> <num_games> <rank|sum|max> <pns_constant>";

    private static String ratio(int wins, int games) {
        if (games <= 0)
            return "NaN";
        return "" + Math.round(1000.0 * wins / games) / 1000.0;
    }

    private static String algoInfo(String GAME_NAME, double TIME_FOR_GAME, int NUM_GAMES, boolean finMove, int minVisits, String pnsMethod, double pnsConstant, String ALGO_NAME, int wins, int draws, int loses1, int loses2, long startTime) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        return String.format("%s: t=%.2fs, num=%d, finMove=%s, solver=%s, uct=%s(%.1f): %s vs MCTS: W: %d D: %d OPP1: %d OPP2: %d, ~RATIO: %s;  T: %.2fh (game: %.2fm)", GAME_NAME, TIME_FOR_GAME, NUM_GAMES, ""+finMove, minVisits==Integer.MAX_VALUE?"No":(""+minVisits), pnsMethod, pnsConstant, ALGO_NAME, wins, draws, loses1, loses2, ratio(wins, NUM_GAMES - draws), (double)elapsedTime/1000/60/60, (double)elapsedTime/NUM_GAMES/1000/60);
    }

    private static void calculateConfidence(Map<String, Integer> results, int draws, String ALGO_NAME) {
        try {
            Process p = Runtime.getRuntime().exec(String.format("python%s scripts/confidence_calculator.py %d %d %d", System.getProperty("os.name").toLowerCase().contains("windows") ? "" : "3", results.get(ALGO_NAME), draws, results.get("MCTS")));
            BufferedReader pout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            {String s = null;; while ((s = pout.readLine()) != null) {System.out.println(StringRoutines.cleanWhitespace(s));}}
            BufferedReader perr = new BufferedReader(new InputStreamReader(p.getErrorStream())); {String s = null; while ((s = perr.readLine()) != null) {System.out.println("!>"+StringRoutines.cleanWhitespace(s));}}
            p.waitFor();
        } catch (Exception e) {e.printStackTrace();}
    }

    public static void main(String[] args) {
        args = new String[]{"500", "RajaPasuMandiri", "200", "rank", "1.0"};

        final int NUM_PLAYERS = 3;
        
        boolean RUN_CI_CALC=false;
        boolean VERBOSE=true;

        if(args.length < 5) {System.err.println(USAGE_ERR); System.exit(1);}

        String GAME_NAME = "games/"+args[1]+".lud";
        File GAME_FILE = new File(GAME_NAME);

        if (!GAME_FILE.canRead()) {System.err.println("Cannot read game file: " + GAME_FILE.getAbsolutePath()); System.exit(1);}

        double TIME_FOR_GAME = Double.parseDouble(args[0])/1000;
        int NUM_GAMES = Integer.parseInt(args[2]);

        MP_PNS_UCB.PNUCT_VARIANT pnsMethod = null;
        switch (args[3]) {
            case "rank": pnsMethod = MP_PNS_UCB.PNUCT_VARIANT.RANK; break;
            case "sum": pnsMethod = MP_PNS_UCB.PNUCT_VARIANT.SUM; break;
            case "max": pnsMethod = MP_PNS_UCB.PNUCT_VARIANT.MAX; break;
            default: System.err.println("Wrong PNS method, please choose rank, sum, or max");System.err.println(USAGE_ERR);System.exit(1);
        }

        double pnsConstant = Double.parseDouble(args[4]);


        // ---------------
        final String ALGO_NAME="pnMCTS_3P";
        System.out.println("Runner - 3P");
        System.out.println(GAME_FILE.getAbsolutePath());

        // load and create game
        final Game game = GameLoader.loadGameFromFile(GAME_FILE);
        final Trial trial = new Trial(game);
        final Context context = new Context(game, trial);

        Map<String, Integer> results = new HashMap<>();
        Map<Integer, Integer> positions = new HashMap<>();
        int draws = 0;

        // HARDCODED params to test:
        boolean finMove = false;
        int minVisits = Integer.MAX_VALUE;// 5;

        long startTime = System.currentTimeMillis();
        for (int gameCounter = 1; gameCounter <= NUM_GAMES; ++gameCounter) {
//            AI testedAI = new PNSMCTS_2P(finMove, minVisits, pnsConstant, pnsMethod);
            //AI testedAI = new  MCTS.(finMove, minVisits, pnsConstant, pnsMethod);
//            AI testedAI = MCTS.createUCT(); // TODO

            List<AI> ais = new ArrayList<>();
            if (gameCounter % NUM_PLAYERS == 0) {
                ais.add(null);
                ais.add(MCTS.createUCT());
                ais.add(MCTS.createUCT());
                ais.add(MCTS.createMPPNSMCTS(pnsConstant, pnsMethod));
            }
            else if (gameCounter % NUM_PLAYERS == 1) {
                ais.add(null);
                ais.add(MCTS.createMPPNSMCTS(pnsConstant, pnsMethod));
                ais.add(MCTS.createUCT());
                ais.add(MCTS.createUCT());
            }
            else if (gameCounter % NUM_PLAYERS == 2) {
                ais.add(null);
                ais.add(MCTS.createUCT());
                ais.add(MCTS.createMPPNSMCTS(pnsConstant, pnsMethod));
                ais.add(MCTS.createUCT());
            }
            
            if (gameCounter == 1) {
            	results.put("MCTS_1", 0); results.put("MCTS_2", 0); results.put(ALGO_NAME, 0);
            	positions.put(1,  0); positions.put(2,  0); positions.put(3,  0);
            }

            game.start(context);
            for (int p = 1; p < ais.size(); ++p) { ais.get(p).initAI(game, p); }
            final Model model = context.model();
            while (!context.trial().over()) { model.startNewStep(context, ais, TIME_FOR_GAME); }
            
            
            for(int p = 1; p < ais.size(); ++p)
            {

            	try {
	            	if(p % NUM_PLAYERS == gameCounter % NUM_PLAYERS)
	            		positions.put((int)(context.trial().ranking()[p]), positions.get((int)context.trial().ranking()[p]) + 1);
            	} catch (Exception e) {
            		//
            	}
            }
            
            int winner = context.trial().status().winner();
            if (winner > 0) {
                if (gameCounter % NUM_PLAYERS == winner % NUM_PLAYERS) {
                    results.put(ALGO_NAME, results.get(ALGO_NAME) + 1);
                } else {
                	String alg = String.format("MCTS_%d", (winner%(NUM_PLAYERS - 1)) + 1); // temporary
                    results.put(alg, results.get(alg) + 1);
                }
            } else {
                ++draws;
            }
            if (VERBOSE) 
            {
            	System.out.println(algoInfo(GAME_NAME, TIME_FOR_GAME, gameCounter, finMove, minVisits, args[3], pnsConstant, ALGO_NAME, results.get(ALGO_NAME), draws, results.get("MCTS_1"), results.get("MCTS_2"), startTime));
            	System.out.println(String.format("Positions claimed: 3: %d, 2: %d, 1: %d", positions.get(3), positions.get(2), positions.get(1)));
            }
        }

        System.out.println("===================");
        System.out.println(algoInfo(GAME_NAME, TIME_FOR_GAME, NUM_GAMES, finMove, minVisits, args[3], pnsConstant, ALGO_NAME, results.get(ALGO_NAME), draws, results.get("MCTS_1"), results.get("MCTS_2"), startTime));
        System.out.println(String.format("Positions claimed: 3: %d, 2: %d, 1: %d", positions.get(3), positions.get(2), positions.get(1)));
        if (RUN_CI_CALC) calculateConfidence(results, draws, ALGO_NAME);
    }
}
