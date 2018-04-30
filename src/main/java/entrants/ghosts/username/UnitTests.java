package entrants.ghosts.username;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.EnumMap;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import entrants.ghosts.username.HeuristicBasedGhost.State;
import javassist.tools.reflect.Sample;
import pacman.Executor;
import pacman.controllers.IndividualGhostController;
import pacman.controllers.MASController;
import pacman.game.Constants.GHOST;
import pacman.game.internal.POType;
import pacman.game.util.Stats;

public class UnitTests {

	private HeuristicBasedGhost testGhost;
	private HeuristicBasedGhost unusedGhost;
	private Stats[] executionResults;
	
	@Before
	public void setUp() throws Exception {
				
		unusedGhost =  new HeuristicBasedGhost(GHOST.BLINKY,false,true);
		testGhost = new HeuristicBasedGhost(GHOST.BLINKY,false,true);
		
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
    	
		EnumMap<GHOST,IndividualGhostController> controllers = new EnumMap<GHOST,IndividualGhostController>(GHOST.class);
		
		controllers.put(GHOST.BLINKY,testGhost);
		controllers.put(GHOST.INKY, new HeuristicBasedGhost(GHOST.INKY,false,true));
		controllers.put(GHOST.PINKY, new HeuristicBasedGhost(GHOST.PINKY,false,true));
		controllers.put(GHOST.SUE, new HeuristicBasedGhost(GHOST.SUE,false,true));
		
	  MASController mas = new MASController(controllers); 
	  Stats[] stats = po.runExperiment(new MyPacMan(),mas,1, "");
	  executionResults = stats;
	}
	
	

	@Test
	public void ghostValuesInitalisedCorrectly() {
		
		assertNotNull(unusedGhost);
		assertSame(unusedGhost.getCurrentState(),State.SEARCHING);
		assertEquals(unusedGhost.getEnumValue(), unusedGhost.getGHOST().ordinal());
		assertTrue(unusedGhost.getEvictingQueue().isEmpty());
		assertTrue(unusedGhost.getMoves().isEmpty());
	}
	
	@Test
	public void ghostExecutionTime() {
		
		double totalTimeMs = testGhost.getTotalExecutionTimeMs();
		double totalTicks = testGhost.getTotalTicks();
		
		System.out.println("Total execution time in (ms): " + totalTimeMs);
		System.out.println("Total number of ticks:	" + totalTicks);
		System.out.println("Result:" + (totalTimeMs/totalTicks));
	
		boolean lessThanTimeBudget = (totalTimeMs/totalTicks)<10;
		assertTrue(lessThanTimeBudget);
	}
	
	
	
	

}
