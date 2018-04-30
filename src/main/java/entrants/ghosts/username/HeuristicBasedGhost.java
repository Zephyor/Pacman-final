package entrants.ghosts.username;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.jenetics.DoubleGene;
import org.jenetics.Genotype;
import org.jenetics.NumericGene;

import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import com.google.common.collect.EvictingQueue;

import entrants.ghosts.username.HeuristicBasedGhost.Pathfinder.Path;
import pacman.Executor;

import pacman.controllers.IndividualGhostController;
import pacman.controllers.MASController;
import pacman.controllers.examples.po.POCommGhost;
import pacman.controllers.examples.po.POGhosts;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.comms.BasicMessage;
import pacman.game.comms.Message;
import pacman.game.comms.Message.MessageType;
import pacman.game.comms.Messenger;
import pacman.game.internal.AStar;
import pacman.game.internal.Maze;
import pacman.game.internal.Node;
import pacman.game.internal.POType;
import pacman.game.util.Stats;
import pacman.game.Constants;
import pacman.game.Game;


public class HeuristicBasedGhost extends IndividualGhostController {

	private int targetIndex,targetTick,lastPacmanIndex,pacmanDirection,nearbyGhosts; //Primitives representing calculated information (pacmanDireciton) or past Information.
	private int nextLiveTick; //Reference to the next tick at which the game will be live.
	private int enumValue; //Value equal to ghost ordinal value, used to distinguish ghosts from one another.
	private int chasingDistance; //Define distance a ghost should start Chasing.
	private boolean setup,randomGhost,Astar; //Flag variables used during game construction
	private boolean targetRequired,targetChecked; //Predicates used during searching
	private State currentState; //Stores the current state 
	private Random rand; //Used to generate random numbers
	private ArrayList<MOVE> nextMoves; //List of the next moves required
	private EvictingQueue<Integer> recentlyVisitedNodes; //Queue that on being full removes the oldest member on a new entry
	private MessageManager mm; //Utility class used during communication
	private Maze_Model model; //Utility class storing original or derived maze data
	private Pathfinder path_finder; //Contains path generating and heuristic functions
	private double timeTaken,tickCounter; //Variables for measuring controller efficiency
	
	/**
	 * Constructor Initialises data structures, variables and inner classes,
	 * and takes operating conditions through parameters.
	 * 
	 * @param ghost - Passes unique enumerated instance 
	 * @param randomGhost - Adds a completely randomly moving ghost if true
	 * @param Astar - Use A* or pre-computed shortest paths (Useful when running multiple games simultaneously)
	 * @param weights - Heuristic function weight used to assigning value to targets
	 */
	
	public HeuristicBasedGhost(GHOST ghost, boolean randomGhost, boolean Astar, ArrayList<Double> weights) { 
		super(ghost); 
		setup=false; //Signifies the game has not been fully setup yet
		currentState = State.SEARCHING; //Ghosts start in the Searching state
		targetIndex = -1; //-1 Used to represent no target
		targetChecked = false; 
		targetRequired = true;
		nearbyGhosts = 0;
		nextMoves = new ArrayList<MOVE>();
		rand = new Random();
		recentlyVisitedNodes = EvictingQueue.create(3);
		targetTick = 0;
		path_finder = new Pathfinder();
		enumValue = ghost.ordinal(); //Assigns value equal to ordinal of enumeration constant (It's position in its enum declaration)
		this.randomGhost = randomGhost;
		this.Astar = Astar;
		setSearchParameters(weights);
		tickCounter = 0.00;
	}
	
	/**
	 * Secondary constructor used when creating game using default search parameters
	 */
	
	public HeuristicBasedGhost(GHOST ghost, boolean randomGhost,boolean Astar) { 
		super(ghost);
		setup=false;
		currentState = State.SEARCHING;
		targetIndex = -1;
		targetChecked = false;
		targetRequired = true;
		nearbyGhosts = 0;
		nextMoves = new ArrayList<MOVE>();
		rand = new Random();
		recentlyVisitedNodes = EvictingQueue.create(3);
		targetTick = 0;
		path_finder = new Pathfinder();
		enumValue = ghost.ordinal();
		this.randomGhost = randomGhost;
		ArrayList<Double> defaultWeights = loadWeightsFromFile("defaultWeights.txt");
		setSearchParameters(defaultWeights);
		tickCounter = 0.00;
		this.Astar = Astar;
	}
	
	public static void main(String[] args) throws IOException {

	        Executor po = new Executor.Builder()
	                .setTickLimit(4000)
	                .setTimeLimit(40)
	                .setGraphicsDaemon(true)
	                .setGhostsMessage(true)
	                .setVisual(true)
	                .setPOType(POType.LOS)
	                .setGhostPO(true)
	                .setPacmanPO(true)
	                .build();
	    	
	        //ArrayList<Double> weights = loadWeightsFromFile("StartPacmanCO.txt"); 
	        ArrayList<Double> weights = loadWeightsFromFile("StartPacmanPO.txt"); 
			EnumMap<GHOST,IndividualGhostController> controllers = new EnumMap<GHOST,IndividualGhostController>(GHOST.class);
			
			controllers.put(GHOST.BLINKY,new HeuristicBasedGhost(GHOST.BLINKY,false,true,weights));
			controllers.put(GHOST.INKY, new HeuristicBasedGhost(GHOST.INKY,false,true,weights));
			controllers.put(GHOST.PINKY, new HeuristicBasedGhost(GHOST.PINKY,false,true,weights));
			controllers.put(GHOST.SUE, new HeuristicBasedGhost(GHOST.SUE,false,true,weights));
			
		  MASController mas = new MASController(controllers); 
		  
		  
		  //Stats[] stats = po.runExperiment(new MyPacMan() ,mas,10, "");
		  //Stats[] stats = po.runExperiment(new MyPacMan2() ,mas,10, "");
		  
		  
		  po.runGame(new MyPacMan(),mas,40);
	    }
	
	/**
	 * Reads an Array of double values from a text file
	 * for heuristics.
	 * 
	 * @param filename - The name of the text file
	 */
	
	public static final ArrayList<Double> loadWeightsFromFile(String filename) {
		ArrayList<Double> weights = new ArrayList<Double>();
		try {
			File file = new File(filename);
			Scanner scanner = new Scanner(file).useDelimiter("/");
			scanner.useLocale(Locale.UK);
			
			while(scanner.hasNext()) {		
				Double nextDouble = scanner.nextDouble();
				weights.add(nextDouble);
			}
			scanner.close();
		}catch(Exception e) {
			System.out.println("Unable to read weights file");
			System.out.println(e.getMessage());
		}
		return weights;
	}
	
	
	/**
	 * Series of Accessor methods used during testing
	 */
	
	public double getTotalExecutionTimeMs() {
		return timeTaken;
	}
	
	public double getTotalTicks() {
		return tickCounter;
	}
	
	public int getTargetTick() {
		return targetTick;
	}
	
	public int getTargetIndex() {
		return targetIndex;
	}
	
	public State getCurrentState() {
		return currentState;
	}
	
	public ArrayList<MOVE> getMoves(){
		return nextMoves;
	}
	
	public EvictingQueue<Integer> getEvictingQueue() {
		return recentlyVisitedNodes;
	}
	
	public int getEnumValue() {
		return enumValue;
	}
	
	public GHOST getGHOST() {
		return ghost;
	}
	
	
	/**
	 * Runs an experiment with a set of ghosts using search values
	 * passed in the form of a DoubleGene that's passed to the controller
	 * through it's constructor. 
	 * 
	 * @param geno - DoubleGene consisting of 12 double chromosome values (0.0-1.0)
	 * @return - Inverse of average score
	 */

	public double getFitnessFunctionJenetics(final Genotype<DoubleGene> geno) {
		
		ArrayList<Double> searchValues = geno.getChromosome().stream().map(NumericGene::doubleValue).collect(Collectors.toCollection(ArrayList::new));
		
		Executor executor = new Executor.Builder()
	                .setTickLimit(4000)
	                .setGraphicsDaemon(true)
	                .setGhostsMessage(true)
	                .setVisual(true)
	                .setPOType(POType.LOS)
	                .setGhostPO(true)
	                .setPacmanPO(true)
	                .build();
	
		EnumMap<GHOST,IndividualGhostController> controllers = new EnumMap<GHOST,IndividualGhostController>(GHOST.class);
		controllers.put(GHOST.BLINKY, new HeuristicBasedGhost(GHOST.BLINKY,false,false,searchValues));
	    controllers.put(GHOST.INKY, new HeuristicBasedGhost(GHOST.INKY,false,false,searchValues));
	    controllers.put(GHOST.PINKY, new HeuristicBasedGhost(GHOST.PINKY,false,false,searchValues));
	    controllers.put(GHOST.SUE, new HeuristicBasedGhost(GHOST.SUE,false,false,searchValues));
		MASController mas = new MASController(controllers);
		
		Stats[] results = executor.runExperiment(new MyPacMan(),mas,500,"");
		return 1/results[0].getAverage();
	}
	
	//0=chasingDist, 1=pillMutliplier, 2=quadrant, 3=perTick, 4=chasingConstant, 5=chasingCornerMultiplier, 6=chasingDirectionMulti
	//7= avoidJunctionMulti, 8=avoidCrossroadMulti, 9=searchingJunctionValue, 10= searchingCrossroadValue, 11 = searchingSightingInterval	
	
	/**
	 * Assigns weights to heuristic function in predefined order and
	 * multiplier deriving chasing distance.
	 * 
	 * @param weights - ArrayList of double wrapper objects
	 */
	
	public void setSearchParameters(ArrayList<Double> weights) { 
		path_finder.setSearchParamters(weights.get(1), weights.get(2), weights.get(3), weights.get(4), weights.get(5), weights.get(6), weights.get(7),weights.get(8), weights.get(9), weights.get(10), weights.get(11));
		chasingDistance = (int) Math.round(weights.get(0)*50);
	}
	
	/**
	 * Check for specific game events and update local view accordingly
	 * @param game - Passed to facilitate game state querying
	 */
	
	public void checkGameState(Game game) { 
		int currentTick = game.getCurrentLevelTime();
	
		if(game.wasPacManEaten()) {	
			nextLiveTick = currentTick + Constants.COMMON_LAIR_TIME; //Add lair time to current tick once pacman eaten
			resetLifeVariables(); //Local view partially reset once Pacman captured
			currentState = State.SEARCHING; //Reset state
		}
		
		if(currentTick>nextLiveTick) {
			mm.addNewMessages(game.getMessenger()); //Get messages if game is live
		}

		if(!model.hasCurrentMaze(game.getMazeIndex())) {  
			model.getMazeData(game.getCurrentMaze(),game.getMazeIndex()); //Get new maze data once new maze detected
			resetLifeVariables(); //Reset current life variables, such as current pacman location.
			mm.reset_newLevel(); //Reset level variables, such as pillCount.
		}
	}
	
	/**
	 * Reset some internal variables in the event on pacman
	 * being captured.
	 * 
	 */

	public void resetLifeVariables() {
		mm.reset_PacmanEaten();
		recentlyVisitedNodes.clear();
		nextMoves.clear();
		targetIndex = -1;
		targetRequired = true;
		targetChecked = false;
		lastPacmanIndex = -1;
		nearbyGhosts = 0;
	}
	
	/**
	 * Checks for observable entities using the game instance and subsequently 
	 * messages about entities of significance (PacMan, Powerpills) also 
	 * checks if the target index is observable. 
	 * 
	 * @param game - Passed to facilitate game state querying
	 */
	
	public void checkObservable(Game game) {
		
		int currentPacManIndex = game.getPacmanCurrentNodeIndex();
		if(currentPacManIndex != -1) {  
			 mm.sendMessage(game.getMessenger(), MessageType.PACMAN_SEEN, currentPacManIndex, game.getCurrentLevelTime());
			 
			if(lastPacmanIndex != -1) {
				pacmanDirection = model.getRelativeDirection(lastPacmanIndex,currentPacManIndex);
				if(mm.getPacmanDirection()!=pacmanDirection) { //If pacman going into a different direction than the one broadcast
					mm.sendMessage(game.getMessenger(), MessageType.PACMAN_HEADING, currentPacManIndex, game.getCurrentLevelTime()); //Message location
				}
			}
		 } 
		
		 for(int i=0;i<game.getPowerPillIndices().length;i++) {
			 if(game.isNodeObservable(game.getPowerPillIndices()[i])){ //Power pill node is observable
					 if(!(game.isPowerPillStillAvailable(i)) && mm.hasPill(i)) { //Node no longer has power ill
						 mm.sendMessage(game.getMessenger(),MessageType.PILL_NOT_SEEN,i,game.getCurrentLevelTime()); //Message pill no longer seen
				}
			}
		 }
		 if(game.isNodeObservable(targetIndex) && currentPacManIndex==-1) {
				targetChecked = true;
		 }
		 
		if(game.getGhostCurrentNodeIndex(ghost)==targetIndex || game.isNodeObservable(targetIndex)) {
				if(!recentlyVisitedNodes.contains(new Integer(targetIndex))) { 
					recentlyVisitedNodes.offer(new Integer(targetIndex)); //Add target index, if observed, to recently visited queue.
			}
		}
		
		nearbyGhosts = 0;
		for(int ghostEnum=0;ghostEnum<GHOST.values().length;ghostEnum++) {
			int ghostValue = GHOST.values()[ghostEnum].ordinal();
	
			if(game.getGhostCurrentNodeIndex(GHOST.values()[ghostEnum])!=-1 && ghostValue!=enumValue) {
				nearbyGhosts+=1;
			}
		}		
	}
	
	/**
	 * Check, based on the state of the game, if a new target is required for
	 * the controller.
	 * 
	 * @return boolean - Does ghost require new target
	 */
	
	public boolean isTargetRequired(int currentTick, State nextState, int currentPacmanIndex) {
		
		boolean targetRequired = false;
		int modRemainder = Math.floorMod(currentTick, 20); //Used to moderate how often a ghost checks if it needs a new target
		int lowerTickRange = enumValue*5; //Ghost will require 5 in every 20 ticks unless no moves remain/state changed.
		int upperTickRange = lowerTickRange+5; 
		boolean checkRequired = lowerTickRange>=modRemainder && modRemainder<upperTickRange || nextMoves.size()<=1 || nextState != currentState; 
								
		if(checkRequired) {
			targetRequired = targetIndex == -1 || nextState != currentState || targetChecked || nextMoves.size()<=1;	
			switch(currentState) { //State specific predicates
			case SEARCHING: //In searching state get a new state if a new Pacman location recieved (through messages)
				targetRequired = targetRequired || (mm.hasNewSighting(currentTick)&&(currentTick-targetTick>9)); 
			break;
			case AVOIDING:
			case CHASING:
				targetRequired = targetRequired || (lastPacmanIndex==-1 && currentPacmanIndex != -1);
			break;
			}
		}
		return targetRequired;
	}
		
	@Override
	public MOVE getMove(Game game, long timeDue) {
	
		double startTime = System.currentTimeMillis();
		
		if(!setup) { //One time setup for game data + MM
			model = new Maze_Model(game.getCurrentMaze(),game.getMazeIndex());
			mm = new MessageManager(game);
			nextLiveTick = 0;
			setup = true;
		}
		
		checkGameState(game); //Check for key game events
		checkObservable(game); //Check observable entities and update local references
		int currentTick = game.getCurrentLevelTime(); //Get the current game tick 
		int currentPacManIndex = game.getPacmanCurrentNodeIndex(); 
		State nextState = getNextState(currentPacManIndex, currentTick, game); //Calculate next state
		targetRequired = isTargetRequired(currentTick, nextState, currentPacManIndex); //Is a target required
		lastPacmanIndex = currentPacManIndex; //Last Pacman location updated for calculating direction
		currentState = nextState; //Current state updated 

		
		 if(game.doesGhostRequireAction(ghost)) {
			 switch(currentState) {
				case SEARCHING:
				case AVOIDING:	
				if(targetRequired) { 
					ArrayList<Integer> potentialTargets = getSearchTargets(game.getCurrentLevelTime(),game); //List of targets supplied based on state
					Path path = path_finder.getBestPath(currentTick,game,currentState,potentialTargets); //Path generated
					nextMoves.clear(); //Clear current stored moves
					nextMoves.addAll(path.ToMoves(game)); //Convert path to ArrayList of moves 
					targetIndex = path.getTargetIndex(); //Final index of the path set as target
					targetTick = currentTick;
					mm.sendMessage(game.getMessenger(), MessageType.I_AM_HEADING, targetIndex, currentTick);
					targetRequired = false;
					targetChecked = false;
				}
				break;
				case CHASING:
					if(targetRequired) {
						
						int mostRecentPacmanSighting = mm.getMostRecentPacManLocation(currentTick);
						int currentIndex = game.getGhostCurrentNodeIndex(ghost);
						boolean indirect = (nearbyGhosts!=0 && (nearbyGhosts > rand.nextInt(6))); //Random chance for ghost that can directly observe Pacman to go to a nearby junction
						
						if(currentPacManIndex!= -1 && !indirect) {
							targetIndex = currentPacManIndex; //Set to target to the last Pacman index
						}
						else {
							int closestJunctionIndex = mostRecentPacmanSighting;
							
							if(!game.isJunction(mostRecentPacmanSighting)) { //If Pacman isnt at a junction
								closestJunctionIndex = model.getNearestJunction(mostRecentPacmanSighting); //Get the closest junction	
							}
					
							HashSet<Integer> junctions = new HashSet<Integer>(); //HashSet initialized to store junctions
							junctions.add(closestJunctionIndex); //Closest pacman index added to the set
							int[] neighbouringJunctions = model.getNextJunctions(closestJunctionIndex); //Closest junctions to sighting junction aquired
							
							for(int i=0;i<neighbouringJunctions.length;i++) {
								junctions.add(neighbouringJunctions[i]); //Get the closest junctions of the junctions closest to Pacman 
								int[] nextJunctions = model.getNextJunctions(neighbouringJunctions[i]); //Adjacent junctions of closest junction to pacman found
								for(int j=0;j<nextJunctions.length;j++) { 
									junctions.add(new Integer(nextJunctions[j])); //Each junction added to the set
								}	
							}				
							int highestScore=0;
							int bestJunction=0;
							for(Integer j:junctions) {
								int score = path_finder.getJunctionScore(j.intValue(), mostRecentPacmanSighting, currentIndex, game.getGhostLastMoveMade(ghost), currentState,game);
								if(score>highestScore && currentIndex!=j.intValue() && j.intValue()!=currentIndex) {
									bestJunction = j.intValue();
									highestScore = score;
								}
							}
							targetIndex = bestJunction;
							targetTick = currentTick;
							mm.sendMessage(game.getMessenger(), MessageType.I_AM_HEADING, targetIndex, currentTick);
							targetRequired = false;
							targetChecked = false;
						}
						Path path_to_target = path_finder.generatePath(game.getGhostLastMoveMade(ghost), currentIndex, targetIndex, game);
						nextMoves.clear();
						nextMoves.addAll(path_to_target.ToMoves(game));			
					}
				break;
			}
			
			
			MOVE next = null;
			
			if(nextMoves.isEmpty()) {
				next = model.getRandomMove(game.getGhostCurrentNodeIndex(ghost),game.getGhostLastMoveMade(ghost));
				targetRequired = true;
			}else {
				next = nextMoves.get(0);
				nextMoves.remove(0);
			}
			
			double endTime = System.currentTimeMillis();
			double elapsedTime = endTime - startTime;
			this.timeTaken += elapsedTime;
			this.tickCounter += 1.00;
			
			return next;
		}
		return null;
	}
	
	/**
	 * State determined by a set of rules based on ghost with
	 * respect to enviroment. 
	 * 
	 * @return - Enumerated State constant
	 * 
	 */
	
	public State getNextState(int currentPacManIndex, int currentTick, Game game) {
	
		int sightingIndex = mm.getMostRecentPacManLocation(currentTick);
		boolean ghostEdible = game.wasPowerPillEaten() || game.isGhostEdible(ghost);
		int distanceToSighting = 0;
		
		if(sightingIndex!=-1) {
			distanceToSighting = game.getShortestPathDistance(game.getGhostCurrentNodeIndex(ghost), sightingIndex);
		}
				
		if(ghostEdible) {
			return State.AVOIDING;
		}
		else if(distanceToSighting <= chasingDistance && sightingIndex != -1) { 
			return State.CHASING;
		}
		else {
			return State.SEARCHING;
		}
	}
	
	/**
	 * Supplies a list of key positions within the maze for searching,
	 * also returns Pacman location if sighted.
	 * 
	 * @return - List of wrapped Integers
	 * 
	 */
	
	public ArrayList<Integer> getSearchTargets(int currentTick, Game game){
		
		ArrayList<Integer> potentialTargets = new ArrayList<Integer>();

		if(currentState == State.SEARCHING || currentState == State.AVOIDING) {
			
			int[] pills = game.getPowerPillIndices();
			
			for(int i=0;i<pills.length;i++) {
				if(game.getGhostCurrentNodeIndex(ghost)!=pills[i]) {
					if(game.getShortestPathDistance(game.getGhostCurrentNodeIndex(ghost), pills[i], game.getGhostLastMoveMade(ghost))>=2) {
						potentialTargets.add(new Integer(pills[i]));
					}
				}
			}
			
			ArrayList<Integer> Crossroads = model.getCrossRoads();
			for(Integer i: Crossroads) {
				if(game.getShortestPathDistance(game.getGhostCurrentNodeIndex(ghost), i.intValue(), game.getGhostLastMoveMade(ghost))>=2) {
					potentialTargets.add(i);
				}
			}
		}
		
		Integer lastPacman = mm.getMostRecentPacManLocation(currentTick);
		
		if(mm.getMostRecentPacManLocation(currentTick)!=-1 && game.getGhostCurrentNodeIndex(ghost)!=lastPacman.intValue()) {
			potentialTargets.add(lastPacman);
		}
		
		return potentialTargets;
	}
	
	/**
	 * Enumerated set each representing a distinctive type of behaviour
	 * 
	 */
	
	public enum State{
		SEARCHING,CHASING,AVOIDING 
	}
	
	/**
	 * Utility class used in communication, facilitates 
	 *sending, receiving and storing message data.
	 * 
	 */
	
	public class MessageManager{
		
		private int lastPacManLocation;
		private int sightingTick;
		private int[] intendedLocations;
		private int[] pillCount; //Array of 0/1s representing respective pill indices presence/absence
		private int tickValue;
		private int pacmanDirection; //N=0 E=1 S=2 W=3
		public static final int RECENT_THRESHOLD = 20;
		
		public MessageManager(Game game) {
			lastPacManLocation = -1; //Last location pacman was seen
			sightingTick = 0; //Last pacman sighting tick
			intendedLocations = new int[GHOST.values().length];
			pillCount = new int[game.getPowerPillIndices().length];
			Arrays.fill(intendedLocations, -1);
			Arrays.fill(pillCount,1);
		}
		
		/**
		 * Check if a new (last 10 ticks) sighting has been received. 
		 */
		
		public boolean hasNewSighting(int currentTick) {
			if(currentTick-sightingTick<11) {
				return true;
			}
			return false;
		}

		public int getTickValue() {
			return tickValue;
		}
		
		/**
		 * Retrieves messages from game Messenger and updates
		 * appropriate variables.
		 * 
		 */		
		
		public void addNewMessages(Messenger messenger) {
				if(messenger!=null) {
				
				ArrayList<Message> list = messenger.getMessages(ghost); //Acquire messages from game messenger object
					for(Message m: list) { //Iterate over messages
						switch(m.getType()) { 
						case PACMAN_SEEN: 
						if(m.getTick() > sightingTick) {
							lastPacManLocation = m.getData(); 
							sightingTick = m.getTick();
						}
							break;
						case PILL_NOT_SEEN:
							pillCount[m.getData()] = 0; //Set pill counter to 0
							break;
						case I_AM_HEADING:
							int sender = m.getSender().ordinal(); //Sender equal to enumerated order of ghost
							intendedLocations[sender] = m.getData();
							break;
						case PACMAN_HEADING:
							pacmanDirection = m.getData();
							break;
					}
				}
				}
			}
		
		/**
		 * Updates class variables and sends a message using
		 * game Messenger object.
		 * 
		 */
		
		public void sendMessage(Messenger messenger, MessageType type, int messageData, int currentTick) {
			
			if(messenger!=null) {
				 switch(type) { //Behaviour based on type of message
				 case I_AM_HEADING:
					 intendedLocations[enumValue] = messageData; //Set intended array equal to unique enumeration number
					 messenger.addMessage(new BasicMessage(ghost, null, MessageType.I_AM_HEADING, messageData, currentTick));
					 break;
				 case PACMAN_SEEN:
					lastPacManLocation = messageData; //Ensure integrity by assigning message data to local reference
					sightingTick = currentTick; 
					messenger.addMessage(new BasicMessage(ghost, null, BasicMessage.MessageType.PACMAN_SEEN, messageData, currentTick));
					break;
				 case PILL_NOT_SEEN: 
					 messenger.addMessage(new BasicMessage(ghost,null, MessageType.PILL_NOT_SEEN, messageData, currentTick));
					 pillCount[messageData] = 0; //Set respective pill index to 0
					 break;
				 case PACMAN_HEADING:
					 messenger.addMessage(new BasicMessage(ghost,null,MessageType.PACMAN_HEADING,messageData,currentTick));
					 break;
				 }
			}
			
		}
		
		public int[] getGhostIntendedDirections() {
			return intendedLocations;
		}
		
		public int getMostRecentPacManLocation(int currentTick) {
			if(currentTick-sightingTick<RECENT_THRESHOLD && lastPacManLocation != -1) {
				return lastPacManLocation;
			}
			else {
				return -1;
			}
		}
		
		public int getMostRecentPacManLocationTick() {
			return sightingTick;
		}
		
		public boolean hasPill(int index) {
			if(pillCount[index]==1) 
			{
				return true;
			}
			else {
				return false;
			}
		}	
			
		public void reset_PacmanEaten() {
			Arrays.fill(intendedLocations, -1);
			sightingTick = -1;
			lastPacManLocation = -1;
		}
		
		public void reset_newLevel() {
			Arrays.fill(pillCount,1);
			Arrays.fill(intendedLocations, -1);
			sightingTick = -1;
			lastPacManLocation = -1;
		}
		
		public int getPacmanDirection() {
			return pacmanDirection;
			}
		}

	/**
	 * Responsible for finding paths between Nodes
	 * and assigning scores to Paths & Junctions.
	 * 
	 */
	
	public class Pathfinder{ //For search path/target
	
		public static final int scoreBaseline = 10; 
	
		private double perTickPercentage;
		private double sharedQuadrantPercentage; 
		private double pillMultiplier; 
		private double chasingCornerMultiplier,chasingConstant, chasingDirectionMultiplier;
		private double avoidingJunctionMultiplier,avoidingCrossroadMultiplier;
		private double searchingJunctionValue, searchingCrossroadValue, searchingSightingInterval;
			
		public void setSearchParamters(double pillMult, double searchQuadrant,double perTick, double chasingCon, double cornerMult, double chasingDirectionMulti, double avoidJunctionMulti, double avoidCrossroadMulti, double searchingJunctionValue, double searchingCrossroadValue, double searchingSightingInterval) {
			perTickPercentage = perTick*0.10;
			sharedQuadrantPercentage = searchQuadrant*0.33;
			pillMultiplier = pillMult+pillMult+pillMult;
			chasingConstant = chasingCon;
			chasingCornerMultiplier = cornerMult;
			avoidingJunctionMultiplier = avoidJunctionMulti;
			avoidingCrossroadMultiplier = avoidCrossroadMulti;
			this.searchingJunctionValue = searchingJunctionValue*5;
			this.searchingCrossroadValue = searchingCrossroadValue*5;
			this.searchingSightingInterval = searchingSightingInterval*30;
			this.chasingDirectionMultiplier = chasingDirectionMulti+chasingDirectionMulti+chasingDirectionMulti; 
		}
		
		public Path generatePath(MOVE lastMoveMade, int startNode, int targetNode, Game game) {

			Path path = null;
			
			if(Astar) {
				AStar a = game.getCurrentMaze().astar; 
				a.resetGraph();
				int[] fullPath = a.computePathsAStar(game.getGhostCurrentNodeIndex(ghost), targetNode, game.getGhostLastMoveMade(ghost), game);
				path = new Path(fullPath,startNode);
				
			}else {
				int[] fullPath = game.getShortestPath(game.getGhostCurrentNodeIndex(ghost), targetNode, lastMoveMade);
				path = new Path(fullPath,startNode);
			}
			return path; 
		}
		
		public int getJunctionScore(int JunctionIndex, int pacmanIndex, int ghostIndex, MOVE lastMoveMade, State state, Game game) {
			
			int score = scoreBaseline;
	
				int distance_pacmanToPoint = game.getShortestPathDistance(JunctionIndex, pacmanIndex, lastMoveMade);
				int distance_ghostToPoint = game.getShortestPathDistance(ghostIndex, JunctionIndex, lastMoveMade);
				int difference = Math.abs(distance_pacmanToPoint - distance_ghostToPoint);
				
				if(distance_pacmanToPoint>0 && distance_pacmanToPoint<=chasingDistance/3) { //Between 0-chasing_distance/3
					score*=(3*chasingConstant);
				}else if(distance_pacmanToPoint<=chasingDistance/3*2) { //Between chasing_distance/3 - chasing_distance/3*2
					score*=(2*chasingConstant);
				}else {
					score*=(1*chasingConstant); //>chasing_distance/3*2
				}
				
				if(difference>=0 && difference<=chasingDistance/3) {
					score*=(3*chasingConstant);
				}else if(difference<=(chasingDistance/3*2)) {
					score*=(2*chasingConstant);
				}else {
					score*=(1*chasingConstant);
				}
				
				int nodeDirection = model.getRelativeDirection(ghostIndex,JunctionIndex);
				int pacmanDirection = mm.getPacmanDirection();
				
				if(nodeDirection==pacmanDirection) {
					score*=chasingDirectionMultiplier;
				}
						
				if(model.isCorner(pacmanIndex)){
					int[] junctions = model.getNextJunctions(pacmanIndex);
					for(int j=0;j<junctions.length;j++) {
						if(JunctionIndex==junctions[j]) {
							score*=(chasingCornerMultiplier*10);
						}
					}
				}
				return score;
		}
	
		public Path getBestPath(int currentTick, Game game, State currentState, ArrayList<Integer> potentialTargets) { //To do: refactor!
				
				double scoreCounter = 0;
				Path best = null;
				int randomIndex = rand.nextInt(potentialTargets.size());
				int currentIndex = 0;
				
				for(Integer l: potentialTargets) {
					Path path = path_finder.generatePath((game.getGhostLastMoveMade(ghost)), game.getGhostCurrentNodeIndex(ghost), l.intValue(),game);
					double val = path_finder.getPathValue(path,currentState, game);
								
					if(randomGhost && currentIndex==randomIndex) {
						return path;
					}
					currentIndex++;
					
					if(val>scoreCounter)  {
						best = path;
						scoreCounter = val;
					}
				}
			return best;
		}
		
		public double getPathValue(Path path, State currentState, Game game) { 
			
			int[] fullPath = path.getFullPath();
			double totalValue = 0;
			int pacManIndex = mm.getMostRecentPacManLocation(game.getCurrentLevelTime());
			int[] intended = mm.getGhostIntendedDirections();
			int[] power = game.getPowerPillIndices();
			int finalIndex = fullPath[fullPath.length-1];
			Node finalNode = model.getGraph()[finalIndex];
			int pathIntendedQuadrant = model.getQuadrantID(finalNode.x, finalNode.y);
			
			switch(currentState) {
			case AVOIDING:
				for(int y=0;y<fullPath.length;y++) {
					double nodeScore = scoreBaseline;
					int currentNode = fullPath[y];
					
					if(pacManIndex!=-1) {
						int distance_to_node = game.getShortestPathDistance(currentNode,pacManIndex);	
						double multiplier = 0;
						if(distance_to_node==0) {
							multiplier = 1;
						}else {
							multiplier = Math.log(distance_to_node) / Math.log(3);
						}
						nodeScore = (nodeScore*multiplier);
					}
					
					if(game.getCurrentMaze().graph[currentNode].numNeighbouringNodes==3) {
						nodeScore*=avoidingJunctionMultiplier;
					}else if(game.getCurrentMaze().graph[currentNode].numNeighbouringNodes==4) {
						nodeScore*=avoidingCrossroadMultiplier;
					}					
					totalValue += (nodeScore);
				}
				
				for(int i=0;i<intended.length;i++) {
					Node intendedNode = game.getCurrentMaze().graph[i];
					if(model.getQuadrantID(intendedNode.x, intendedNode.y)==pathIntendedQuadrant) {
						totalValue*=sharedQuadrantPercentage;
					}
				}
				
				int[] pills = game.getPowerPillIndices();
				for(int p=0;p<pills.length;p++) {
					if(!(pills[p]==finalNode.nodeIndex)) {
						totalValue *= pillMultiplier;
					}
				}
		
				totalValue = totalValue/path.fullPath.length; //Length normalise

				return totalValue;
				
			case SEARCHING:
				for(int i=0;i<fullPath.length;i++) {
					int nodeIndex = fullPath[i];
					double nodeValue = scoreBaseline;
					if(model.getGraph()[nodeIndex].numNeighbouringNodes==4) {
						nodeValue=nodeValue*searchingJunctionValue;
					}
					else if(model.graph[nodeIndex].numNeighbouringNodes==3) {
						nodeValue=nodeValue*searchingCrossroadValue;
					}
					totalValue += nodeValue;
				}
						
				if(pacManIndex!=-1) {
					
					double sighting_distance_multiplier = 1.00;
					double sighting_timing_multiplier = 1.00;
					
					int distanceFromSighting = game.getShortestPathDistance(finalIndex, pacManIndex);
					int sightingTick = mm.getMostRecentPacManLocationTick();					
					int count = (int) Math.ceil(distanceFromSighting/searchingSightingInterval);
					
					if(count<1) {
						count=1;
					}
					
					double maxDistanceMultiplier = 6.0;
					sighting_distance_multiplier = maxDistanceMultiplier/count;
					
					int tickDifference = game.getCurrentLevelTime()-sightingTick;
					sighting_timing_multiplier = 1-(perTickPercentage*tickDifference);
					
					if(sighting_timing_multiplier<=0) {
						sighting_timing_multiplier=0.05;
					}
									
					double sightingMultiplier = sighting_timing_multiplier*sighting_distance_multiplier;
					totalValue = totalValue*sightingMultiplier; 
				}
				
				for(Integer lastVisitedLocation:recentlyVisitedNodes) {
					if(lastVisitedLocation==finalIndex) {
						totalValue = totalValue*0.5; 
					}
				}
				
				int quadrantIntendedCount = 0;
				for(int y=0;y<intended.length;y++) {
					if(intended[y]!=-1) {
						Node intendedNode = model.graph[intended[y]];
						int intendedQuadrantID = model.getQuadrantID(intendedNode.x, intendedNode.y);
						if(intendedQuadrantID == pathIntendedQuadrant) {
							quadrantIntendedCount++;
						}
					}
				}
				
				if(quadrantIntendedCount!= 0) {
					double intentionMultiplier = 1 - (sharedQuadrantPercentage*quadrantIntendedCount);
					totalValue*=intentionMultiplier;
				}
				
				for(int p=0;p<power.length;p++) {
					if(finalNode.nodeIndex==power[p]) {
						if(mm.hasPill(p)) {
							totalValue*=pillMultiplier;
						}
					}	
				}
				
				totalValue = totalValue/path.fullPath.length;
				return totalValue;
			}
		return -1;
	}
	public class Path{
		
		private int[] fullPath;
		public int[] junctionIndicies;
		private int startingIndex;
		
		public Path(int[] path, int currentIndex){
			this.fullPath = path;
			startingIndex = currentIndex;
		}
	
		public ArrayList<MOVE> ToMoves(Game game) {
			
			ArrayList<MOVE> moves = new ArrayList<MOVE>();
			
			if(fullPath.length<=1) {
				moves.add(game.getNextMoveTowardsTarget(game.getGhostCurrentNodeIndex(ghost), fullPath[0] , DM.PATH));
				return moves;
			}
	
			Node[] graph = game.getCurrentMaze().graph;
			
			int firstIndex = fullPath[0];
			EnumMap<MOVE, Integer> startingHood = graph[startingIndex].neighbourhood;
			Set<Entry<MOVE, Integer>> startEntry = startingHood.entrySet();
			
			if(!(firstIndex==startingIndex)){ //Then startingIndex is emitted in path, find move to FIRST node index
				for(Map.Entry<MOVE,Integer> entry : startEntry) {
					if(entry.getValue().intValue()==fullPath[0]){
						moves.add(entry.getKey());
					}
				}	
			}
			else {
				for(Map.Entry<MOVE,Integer> entry : startEntry) {
					if(entry.getValue().intValue()==fullPath[1]){
						moves.add(entry.getKey());
					}
				}
			}
						
			for(int i=0;i<fullPath.length;i++) {
				if(!(i+1>=fullPath.length)) {
	
					int nextIndex = fullPath[i+1];
					EnumMap<MOVE, Integer> hood = graph[fullPath[i]].neighbourhood;
					boolean isJunction = graph[fullPath[i]].numNeighbouringNodes>2;
					
					if((model.isCorner(graph[fullPath[i]].nodeIndex)||isJunction) && i!=0) { 															
						MOVE nextMove = null;					
						Set<Entry<MOVE, Integer>> entrySet = hood.entrySet();
						for(Map.Entry<MOVE,Integer> entry : entrySet) {
							int adjacentIndex = entry.getValue();
							if(adjacentIndex==nextIndex) {
								nextMove = entry.getKey();
								moves.add(nextMove);
								break;
							}
						}
					}
				}
			}
			
			return moves;
		}
		
		public int[] getFullPath() {
			return fullPath;
		}
		
		public int getTargetIndex() {
			return fullPath[fullPath.length-1];
		}
	}
	}
	
	
	/**
	 * Calculates and stores key maze information accessed 
	 * during Heuristics and target acquisition. 
	 * 
	 */
	
	public class Maze_Model{
		
		private HashMap<Integer,int[]> nearbyJunctions; 
		private ArrayList<Integer> crossRoads;
		private Quadrant[] quadrants;
		private Node[] graph;
		private ArrayList<Integer> corners;
		private Maze currentMaze;
		private int currentMazeIndex;
		
		public Maze_Model(Maze maze, int mazeIndex) {
			nearbyJunctions = new HashMap<>();
			quadrants = new Quadrant[4];
			graph = maze.graph;
			corners = new ArrayList<Integer>();
			crossRoads = new ArrayList<Integer>();
			getMazeData(maze,mazeIndex);
		}
		
		public boolean hasCurrentMaze(int mazeIndex) {
			return currentMazeIndex==mazeIndex;
		}
		
		public Node[] getGraph() {
			return graph;
		}

		public boolean isCorner(int nodeIndex) {
			return corners.contains(new Integer(nodeIndex));
		}
		
		public int getRelativeDirection(int start, int ending) {
			
			Node startNode = currentMaze.graph[start];
			Node endingNode = currentMaze.graph[ending];
			int xDiff = startNode.x - endingNode.x; 
			int yDiff = startNode.x - endingNode.x;
			
			if(Math.abs(xDiff)>Math.abs(yDiff)) {
				if(xDiff<0) {
					return 3;
				}else {
					return 1;
				}
			}else {
				if(yDiff<0) {
					return 2;
				}else {
					return 0;
				}		
			}
		}
		
		public MOVE getRandomMove(int fromNode, MOVE lastMoveMade) {
			
			if(fromNode==currentMaze.lairNodeIndex) {
				Set<MOVE> moves = currentMaze.graph[currentMaze.initialGhostNodeIndex].neighbourhood.keySet();
				Iterator<MOVE> move_arr = moves.iterator();
				return move_arr.next();
			}
			
		   Set<MOVE> moves = graph[fromNode].neighbourhood.keySet();
		   Iterator<MOVE> move_arr = moves.iterator();
		   int rand_move = rand.nextInt(moves.size());
		   int tmp =0;
		   while(move_arr.hasNext() && tmp!=rand_move) {
			   if(rand_move==tmp && move_arr.next() != lastMoveMade.opposite()) {
				   return move_arr.next();
			   }
			   tmp++;
		   }
		   return null;
		}
		
		public void getMazeData(Maze maze, int mazeIndex) { //Called on new maze (next level)
			currentMaze = maze;
			currentMazeIndex = mazeIndex;
			nearbyJunctions.clear(); //Reset any existing maze data
			corners.clear();
			crossRoads.clear();
			graph = maze.graph; //Local reference to maze graph (Array of Nodes)
			int xCount = 0; //Counters used to find mid-point in the maze for Quadrants
			int yCount = 0;
			
			for(int y=0;y<graph.length;y++) { //Iterate over each node in the graph and classify all corner instances
				Node currentNode = graph[y];
				EnumMap<MOVE,Integer> hood = currentNode.neighbourhood;
				Set<MOVE> moves = hood.keySet(); //Get potential moves from node
				if(currentNode.numNeighbouringNodes==2) { 
					boolean isCorner = true;
					for(MOVE m: moves) { 
						int adjacentNode = hood.get(m);
						EnumMap<MOVE,Integer> nextHood = graph[adjacentNode].neighbourhood;
						Set<MOVE> moveSet = nextHood.keySet();
						
						if((graph[adjacentNode].numNeighbouringNodes!=2) || (moves.equals(moveSet))) { 
							isCorner = false; //If an adjacent node has the same move set as the node/is a junction then it is NOT a corner 
						}
					}
					if(isCorner && !corners.contains(new Integer(currentNode.nodeIndex))) {
						corners.add(currentNode.nodeIndex); //Add to corner list
					}
				}
			}
			for(int i=0;i<graph.length;i++) { //Iterate over each node in the path
				Node n = graph[i];
				int x = n.x; //Get X coordinate of node
				int y = n.y; //Get Y coordinate of node
				if(n.numNeighbouringNodes > 2 || corners.contains(new Integer(n.nodeIndex))) { //If node is a junction or corner
					int[] nextJunctions = new int[n.numNeighbouringNodes];	
					EnumMap<MOVE,Integer> hood = n.neighbourhood;
					Set<MOVE> movesFromJunction = hood.keySet(); //Get moves from the current node
					int array_counter = 0;
					
					for(MOVE m: movesFromJunction) { //In each move direction
						Integer adjacentNode = hood.get(m);
						int hoodSize = graph[adjacentNode].numNeighbouringNodes;
						EnumMap<MOVE,Integer> adjacentHood = graph[adjacentNode].neighbourhood;
						
						while(adjacentHood.containsKey(m) && hoodSize==2 && (!corners.contains(adjacentNode))) { //Not a junction or corner
							adjacentNode = adjacentHood.get(m); //Get the next adjacent node
							adjacentHood = graph[adjacentNode].neighbourhood; //Get adjacent neighbourhood
							hoodSize = adjacentHood.size(); 
						}
						nextJunctions[array_counter] = adjacentNode; //Set adjacent junction to respective array index
						array_counter++;  //Increment array index
					}
					nearbyJunctions.put(new Integer(n.nodeIndex),nextJunctions); //Map node Index to respective adjacent junctions
				}
			
				if(n.neighbourhood.size()==4) {
					Integer crossroad_index = new Integer(n.nodeIndex);
					crossRoads.add(crossroad_index);
				}
				if(x>xCount) {
					xCount = x;
				}
				
				if(y>yCount) {
					yCount = y;
				}
			}
			int xMid = Math.round(xCount/2); //X-mid point equal to highest X-value divided by two
			int xMax = Math.round(xCount); 
			int yMid = Math.round(yCount/2); ///Y-mid point equal to highest X-value divided by two
			int yMax = Math.round(yCount);
			Quadrant topLeft = new Quadrant(0,xMid,0,yMid,0); //Quadrant set with 0,0 being the top left corner 
			Quadrant topRight = new Quadrant(xMid,xMax,0,yMid,1); 
			Quadrant botLeft = new Quadrant(0,xMid,yMid,yMax,2);
			Quadrant botRight = new Quadrant(xMid,yMax,yMid,yMax,3);
			setQuadrants(topLeft, topRight, botLeft, botRight);
		}
		
		public int[] getNextJunctions(int vectorIndex){
			return nearbyJunctions.get(new Integer(vectorIndex));
		}
		
		public int getNearestJunction(int nodeIndex) {
		
			EnumMap<MOVE, Integer> hood = graph[nodeIndex].neighbourhood;
			int shortestDistance = 100000;
			int closestJunctionIndex = 0;
			Node finalNode = graph[nodeIndex];
			
			for(MOVE m:hood.keySet()) { //Find closest junction if the current node is not a junction
				int dist_counter = 0;
				while(hood.containsKey(m) && hood.size()<3 && !(corners.contains(finalNode.nodeIndex))) {
					finalNode = graph[hood.get(m)];
					hood = finalNode.neighbourhood;
					dist_counter++;
				}
			
				if(dist_counter<shortestDistance) {
					closestJunctionIndex = finalNode.nodeIndex;
				}
			}
			return closestJunctionIndex;
		}
		
		public void setQuadrants(Quadrant q1, Quadrant q2, Quadrant q3, Quadrant q4) {
			quadrants[0] = q1;
			quadrants[1] = q2;
			quadrants[2] = q3;
			quadrants[3] = q4;
		}
		
		public ArrayList<Integer> getCrossRoads() {
			return crossRoads;
		}
		
		public int getQuadrantID(int x, int y) {
			for(int id=0;id<quadrants.length;id++) {
				Quadrant q = quadrants[id];
				if(q.inQuadrant(x, y)) {
					return id;
				}
			}
			return -1;
		}
	}

	
	
	public class Quadrant{
		private int xRange_min;
		private int xRange_max;
		private int yRange_min;
		private int yRange_max;
		private int id;
		
		public Quadrant(int xRangeMin, int xRangeMax, int yRangeMin, int yRangeMax, int id) {
			xRange_min = xRangeMin;
			xRange_max = xRangeMax;
			yRange_min = yRangeMin;
			yRange_max = yRangeMax;
		}

		public boolean inQuadrant(int x, int y) {
			if((((x>=xRange_min)&&(x<=xRange_max)) && ((y>=yRange_min) && (y<=yRange_max)))) {
				return true;
			}
			else {
				return false;
			}
	}
	public int getId() {
		return id;
	}
}
}
