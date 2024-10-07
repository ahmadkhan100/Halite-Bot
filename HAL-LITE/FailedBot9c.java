// Changelog:
// v1: refactor from python
// v2: targeting neutral square to do most damage possible
// v3: fixed diffusion algo to make less collisions
// v4: optimized the growth period
// v5: bias diffusion units towards battles, param optimized
// v6: collision resolution v1
// v7: realized that collisions are capped before fighting damage is done. Improved collision resolution to v2
// v8: welp.. looks like resolving inner collisions are good too
// v8b: refactored code
// v8c: if 255 behind outerRing, go.
// v8d: look further when picking direction to fight
// v9: implemented the ben spector opening
// v9c: tried to disperse my outer ring a bit more

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.io.PrintWriter;
import java.util.Comparator;

// TODO: if winning in production, wait for strength?
// TODO: Waiting for opponent to take neutral: low priority because it doesn't happen very often

@SuppressWarnings("unchecked")
public class FailedBot9c {
  // Global variables
  static GameMap gameMap;
  static InitPackage iPackage;
  static final int NEUTRAL = 0;
  static final double MOVE_EFFICIENCY_MINIMUM = 0.8;
  static int myID;

  static Map<Integer, Integer> ownerProduction = new HashMap();
  static Map<Integer, Integer> ownerStrength = new HashMap();
  static Map<Integer, Integer> ownerTerritory = new HashMap();
  static Map<Location, OuterRingMemory> outerRing = new HashMap();
  static Map<Location, Direction> noMansLand = new HashMap();
  static Map<Location, InnerAreaMemory> innerArea = new HashMap();
  static Map<Location, Integer> damageMap = new HashMap();
  static boolean engagedEnemy = false;
  static boolean expansionMode = true;
  static PrintWriter log;

	public static void main(String[] args) throws Exception {
    boolean isFirstCycle = true;
		iPackage = Networking.getInit();
		myID = iPackage.myID;
		gameMap = iPackage.map;
    log = new PrintWriter("./log.txt", "UTF-8");

		Networking.sendInit("FailedBot9c");

		while(true) {
      // Cycle variables
			ArrayList<Move> moves = new ArrayList<Move>();
      ownerProduction.clear();
      ownerStrength.clear();
      ownerTerritory.clear();
      outerRing.clear();
      noMansLand.clear();
      innerArea.clear();
      damageMap.clear();
			gameMap = Networking.getFrame();

      if (isFirstCycle) {
        initiate(); isFirstCycle = false;
      }

      initialSweep(gameMap);

      outerRingStrategy();

      if (expansionMode) {
        greedyExpand();
        for (Location l : innerArea.keySet()) {
          moves.add(new Move(l, innerArea.get(l).dir));
        }
        Networking.sendFrame(moves); continue;
      }

      generateInnerAreaDiffusionMap();

//    if (!engagedEnemy) {
//      neutralDoubleTeaming(); 
//    }
  
      resolveCollisions();

      for (Location l : outerRing.keySet()) {
        moves.add(new Move(l, outerRing.get(l).dir));
      }
      for (Location l : innerArea.keySet()) {
        moves.add(new Move(l, innerArea.get(l).dir));
      }
			Networking.sendFrame(moves);
		}
	}

  private static void initiate() {
    Location initialLoc = new Location(0,0);
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location loc = new Location(x, y);
        Site site = gameMap.getSite(loc);
        if (site.owner == myID) {
          initialLoc = loc;
        }
      }
    }

//  int surroundingStrength = 0;
//  int surroundingProd = 0;
//  for(int y = 0; y < gameMap.height; y++) {
//    for(int x = 0; x < gameMap.width; x++) {
//      Location loc = new Location(x, y);
//      Site site = gameMap.getSite(loc);
//      if (gameMap.getDistance(initialLoc, loc) < 6) {
//        surroundingStrength += site.strength;
//        surroundingProd += site.production;
//      }
//    }
//  }

//  if ((double)surroundingStrength / surroundingProd < 5.0) {
//    expansionMode = false;
//  }

  }

//private static computeBestNeutralPath(Location from, int strgLeft, Direction d1, Direction d2) {

//}

  private static void greedyExpand() {
    int MAX_DEPTH = 255;
    for (Location l : outerRing.keySet()) {
      innerArea.put(l, new InnerAreaMemory(Direction.STILL, Integer.MAX_VALUE));
    }

    ArrayList<Location> sortedNoMansLand = new ArrayList<Location>(noMansLand.keySet());
    Collections.sort(sortedNoMansLand, new Comparator<Location>() {
      public int compare(Location l1, Location l2) {
        Site s1 = gameMap.getSite(l1);
        Site s2 = gameMap.getSite(l2);
        return new Double((double)s2.production / (s2.strength+1)).compareTo((double)s1.production / (s1.strength+1));
      }
    });

    Set<Location> used = new HashSet();
    for (Location l : sortedNoMansLand) {
      Site targetSite = gameMap.getSite(l);
      int targetStrg = targetSite.strength;
      Deque<LocNode> toVisitQueue = new LinkedList();
      for (Direction d : Direction.CARDINALS) {
        Location nextLoc = gameMap.getLocation(l, d);
        if (innerArea.containsKey(nextLoc) && !used.contains(nextLoc)) {
          Set<Location> hs = new HashSet(); hs.add(nextLoc);
          toVisitQueue.add(new LocNode(nextLoc, hs, targetStrg, 0, 1, d));
        }
      }
      while (!toVisitQueue.isEmpty()) {
        LocNode cur = toVisitQueue.removeFirst();
        Site curSite = gameMap.getSite(cur.loc);
        if (cur.targetStrg < curSite.strength) { // backtrack
          innerArea.put(cur.loc, new InnerAreaMemory(Direction.reverseDirection(cur.lastDirection), cur.depth));
          for (Location visited : cur.visited) {
            used.add(visited);
          }
          break;
        }
        else { // We must go deeper
          for (Direction d : Direction.CARDINALS) {
            Location nextLoc = gameMap.getLocation(cur.loc, d);
            if (innerArea.containsKey(nextLoc) && !used.contains(nextLoc) && !cur.visited.contains(nextLoc) && cur.depth < MAX_DEPTH) {
              cur.visited.add(nextLoc);
              toVisitQueue.add(new LocNode(
                    nextLoc, 
                    cur.visited, 
                    targetStrg - (cur.pathProductionSum + gameMap.getSite(cur.loc).strength),
                    cur.pathProductionSum + gameMap.getSite(cur.loc).production,
                    cur.depth + 1,
                    d));
            }
          }
        }
      }
    }

//  if (ownerTerritory.get(myID) - used.size() > 20) {
//    expansionMode = false;
//  }
  }


  private static void initialSweep(GameMap gameMap) {
    for(int y = 0; y < gameMap.height; y++) {
      for(int x = 0; x < gameMap.width; x++) {
        Location loc = new Location(x, y);
        Site site = gameMap.getSite(loc);

        // Compute player stats
        if (site.owner != NEUTRAL) {
          if (ownerProduction.containsKey(site.owner)) {
            ownerProduction.put(site.owner, ownerProduction.get(site.owner) + site.production);
            ownerStrength.put(site.owner, ownerStrength.get(site.owner) + site.strength);
            ownerTerritory.put(site.owner, ownerTerritory.get(site.owner) + 1);
          } else {
            ownerProduction.put(site.owner, site.production);
            ownerStrength.put(site.owner, site.strength);
            ownerTerritory.put(site.owner, 1);
          }
        }

        // Find outer ring, inner area, and noMansLand
        if (site.owner == myID) {
          for (Direction d : Direction.CARDINALS) {
            if (gameMap.getSite(loc, d).owner != myID) {
              outerRing.put(loc, new OuterRingMemory(Direction.STILL, false));
              noMansLand.put(gameMap.getLocation(loc, d), Direction.STILL);
            }
          }
          if (!outerRing.containsKey(loc)) {
            InnerAreaMemory m = new InnerAreaMemory(Direction.STILL, Integer.MAX_VALUE);
            innerArea.put(loc, m);
          }
        }

        // Populate damage map
        damageMap.put(loc, 0);
      }
    }
  }

  private static void neutralDoubleTeaming() {
    for (Location l : noMansLand.keySet()) {
      int stillStrengthSum = 0;
      boolean isEnemyAdjacent = false;
      for (Direction d : Direction.CARDINALS) {
        Location nextLoc = gameMap.getLocation(l, d);
        Site nextSite = gameMap.getSite(nextLoc);
        if (nextSite.owner == myID && outerRing.get(nextLoc).dir == Direction.STILL) {
          stillStrengthSum += nextSite.strength;
        } else if (nextSite.owner != 0) {
          isEnemyAdjacent = true;
        }
      }
      if (!isEnemyAdjacent &&
          stillStrengthSum > gameMap.getSite(l).strength && 
          stillStrengthSum < 1.2 * gameMap.getSite(l).strength) {
        for (Direction d : Direction.CARDINALS) {
          Location nextLoc = gameMap.getLocation(l, d);
          Site nextSite = gameMap.getSite(nextLoc);
          if (nextSite.owner == myID && outerRing.get(nextLoc).dir == Direction.STILL) {
            outerRing.put(nextLoc, new OuterRingMemory(Direction.reverseDirection(d), false));
          }
        }
      }
    }
  }

  private static void resolveCollisionsHelper(Deque<Location> toResolve) {
    while (!toResolve.isEmpty()) {
      Location l = toResolve.removeFirst();
      Site s = gameMap.getSite(l);
      int currentStrength = 0;
      if (outerRing.get(l) != null && outerRing.get(l).dir == Direction.STILL ||
          innerArea.get(l) != null && innerArea.get(l).dir == Direction.STILL) {
        currentStrength = s.strength + s.production;
      }
      for (Direction d : Direction.CARDINALS) {
        Location checkloc = gameMap.getLocation(l, d);
        Site checksite = gameMap.getSite(checkloc);
        if (innerArea.containsKey(checkloc) && innerArea.get(checkloc).dir == Direction.reverseDirection(d)) {
          if ((double)(Math.min(255, currentStrength + checksite.strength) - currentStrength) / checksite.strength > MOVE_EFFICIENCY_MINIMUM) {
            currentStrength += checksite.strength;
          }
          else {
            innerArea.put(checkloc, new InnerAreaMemory(Direction.STILL, innerArea.get(checkloc).distToOuterRing));
            toResolve.add(checkloc);
          }
        }
      }
    }
  }

  private static void resolveCollisions() {
    Deque<Location> toResolve = new LinkedList();
    for (Location l : noMansLand.keySet()) { 
      // I believe incoming strengths are combined and capped before they do damage to neutrals
      // Hence, the logic is almost the same as the outerRing
      Site s = gameMap.getSite(l);
      int currentStrength = 0;
      for (Direction d : Direction.CARDINALS) {
        Location checkloc = gameMap.getLocation(l, d);
        Site checksite = gameMap.getSite(checkloc);
        if (outerRing.containsKey(checkloc) && outerRing.get(checkloc).dir == Direction.reverseDirection(d)) {
          if ((double)(Math.min(255, currentStrength + checksite.strength) - currentStrength) / checksite.strength > MOVE_EFFICIENCY_MINIMUM) {
            currentStrength += checksite.strength;
          }
          else {
            outerRing.put(checkloc, new OuterRingMemory(Direction.STILL, outerRing.get(checkloc).isFighting));
          }
        }
      }
    }
    for (Location l : outerRing.keySet()) {
      toResolve.add(l);
    }
    resolveCollisionsHelper(toResolve);
    for (Location l : innerArea.keySet()) {
      toResolve.add(l);
    }
    resolveCollisionsHelper(toResolve);
  }

  private static void outerRingStrategy() {
    for (Location l : outerRing.keySet()) {
      double highestProdRatio = 0.0;
      Direction prodRatioDirection = Direction.STILL;
      Direction enemyDirection = Direction.STILL;
      int highestPossibleEnemyDamage = 0;
      int lowestStr = 256;
      Site current = gameMap.getSite(l);
      boolean isNextToInner255 = false;
      for (Direction d : Direction.CARDINALS) {
        Location targetLoc = gameMap.getLocation(l, d);
        Site targetSite = gameMap.getSite(l, d);
        if (innerArea.containsKey(targetLoc) && targetSite.strength == 255) {
          isNextToInner255 = true;
          break;
        }
      }
      for (Direction d : Direction.CARDINALS) {
        Location targetLoc = gameMap.getLocation(l, d);
        Site target = gameMap.getSite(targetLoc);

        // Target highest accessible prod ratio
        boolean wontDieAgainstNeutral = current.strength > target.strength;
//      Site targetAdjSite1 = gameMap.getSite(targetLoc, d);
//      Site targetAdjSite2 = gameMap.getSite(targetLoc, Direction.turnLeft(d));
//      Site targetAdjSite3 = gameMap.getSite(targetLoc, Direction.turnRight(d));
//      int enemyStr1 = (targetAdjSite1.owner != 0 && targetAdjSite1.owner != myID) ? targetAdjSite1.strength : 0;
//      int enemyStr2 = (targetAdjSite2.owner != 0 && targetAdjSite2.owner != myID) ? targetAdjSite2.strength : 0;
//      int enemyStr3 = (targetAdjSite3.owner != 0 && targetAdjSite3.owner != myID) ? targetAdjSite3.strength : 0;
//      boolean wontDieAgainstEnemy = current.strength > enemyStr1 && current.strength > enemyStr2 && current.strength > enemyStr3;
//      boolean moveCondition = (wontDieAgainstNeutral && wontDieAgainstEnemy)  || isNextToInner255;
        boolean moveCondition = wontDieAgainstNeutral || isNextToInner255;

        if (target.owner != myID && moveCondition) {
          double targetProdRatio = (double)target.production / (target.strength+1) + 0.0001; // Take squares with zero production
          //double targetProdRatio = (double)target.production / (target.strength+current.production+1) + 0.0001; // Take squares with zero production
          if (targetProdRatio > highestProdRatio) {
            prodRatioDirection = d;
            highestProdRatio = (double)target.production / (target.strength+1);
          }
        }

        // Target enemy 
        // Code could be slightly faster here. Don't double check sites
        int possibleEnemyDamage = 0;
        int possibleOverDamage = 0;
        if (target.owner != myID && target.strength == 0) {
          // Begin triangle calc
          Site potentialEnemy;
          potentialEnemy = gameMap.getSite(targetLoc, d);
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(targetLoc));
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(targetLoc));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
          }
          potentialEnemy = gameMap.getSite(targetLoc, Direction.turnLeft(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(targetLoc));
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnLeft(d))));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(targetLoc));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnLeft(d))));
          }
          potentialEnemy = gameMap.getSite(targetLoc, Direction.turnRight(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(targetLoc));
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnRight(d))));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(targetLoc));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnRight(d))));
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, d), d);
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, d), Direction.turnLeft(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnLeft(d))));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnLeft(d))));
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, d), Direction.turnRight(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnRight(d))));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, d)));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnRight(d))));
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, Direction.turnLeft(d)), Direction.turnLeft(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnLeft(d))));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnLeft(d))));
          }
          potentialEnemy = gameMap.getSite(gameMap.getLocation(targetLoc, Direction.turnRight(d)), Direction.turnRight(d));
          if (potentialEnemy.owner != myID && potentialEnemy.owner != 0) {
            possibleEnemyDamage += calcPotentialDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnRight(d))));
            possibleOverDamage += calcOverDamage(current.strength, potentialEnemy.strength, damageMap.get(gameMap.getLocation(targetLoc, Direction.turnRight(d))));
          }
          // End triangle calc
          
          if (possibleEnemyDamage > highestPossibleEnemyDamage && possibleEnemyDamage > possibleOverDamage) {
            enemyDirection = d;
            highestPossibleEnemyDamage = possibleEnemyDamage;
          }
        }
      }

      Direction chosenDirection = Direction.STILL;
      boolean engagingEnemy = false;
      if (enemyDirection != Direction.STILL) {
        engagedEnemy = true;
        expansionMode = false;
        chosenDirection = enemyDirection;
      } else {
        chosenDirection = prodRatioDirection;
      }
      outerRing.put(l, new OuterRingMemory(chosenDirection, engagingEnemy));
      for (Direction d : Direction.DIRECTIONS) {
        Location splashLoc = gameMap.getLocation(gameMap.getLocation(l, chosenDirection), d);
        damageMap.put(splashLoc, Math.min(255, damageMap.get(splashLoc) + current.strength));
      }
    }
  }

  private static int calcPotentialDamage(int myStr, int enemyStr, int splashStr) {
    return Math.min(Math.max(enemyStr - splashStr, 0), myStr);
  }

  private static int calcOverDamage(int myStr, int enemyStr, int splashStr) {
    return Math.min(Math.min(enemyStr, splashStr), myStr);
  }

  private static void generateInnerAreaDiffusionMap() {
    Direction[] SHUFFLED_CARDINALS = Direction.CARDINALS.clone();
    Collections.shuffle(Arrays.asList(SHUFFLED_CARDINALS));
    for (Direction d : SHUFFLED_CARDINALS) {
      for (Location l : noMansLand.keySet()) {
        Location newLoc = gameMap.getLocation(l, d);
        Site newSite = gameMap.getSite(newLoc);
        int newDist = 1;
        while (innerArea.containsKey(newLoc) || outerRing.containsKey(newLoc)) {
          // if outer ring is fighting, send more this way!
          if (outerRing.containsKey(newLoc) && outerRing.get(newLoc).isFighting) {
            newDist -= 2;
          }

          if (innerArea.containsKey(newLoc) &&
              newDist < innerArea.get(newLoc).distToOuterRing && 
              newSite.strength >= 5*newSite.production) {
            innerArea.put(newLoc, 
                new InnerAreaMemory(Direction.reverseDirection(d), newDist));
          }
          newDist++;
          newLoc = gameMap.getLocation(newLoc, d);
          newSite = gameMap.getSite(newLoc);
        }
      }
    }
  }

}
