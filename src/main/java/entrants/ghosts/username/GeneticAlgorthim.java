package entrants.ghosts.username;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.jenetics.DoubleChromosome;
import org.jenetics.DoubleGene;
import org.jenetics.Genotype;
import org.jenetics.MultiPointCrossover;
import org.jenetics.Mutator;
import org.jenetics.NumericGene;
import org.jenetics.SinglePointCrossover;
import org.jenetics.TournamentSelector;
import org.jenetics.engine.Engine;
import org.jenetics.engine.EvolutionResult;
import org.jenetics.engine.limit;
import org.jenetics.util.Factory;

import pacman.game.Constants.GHOST;

public class GeneticAlgorthim {
	
	   public static void main(String[] args) throws FileNotFoundException {

	       	HeuristicBasedGhost ghost = new HeuristicBasedGhost(GHOST.BLINKY,false,false); //Sample controller created to specify fitness method
	       	
	        final Factory<Genotype<DoubleGene>> gtf = Genotype.of(
	                DoubleChromosome.of(0, 1, 12) //Each gene contains 12 double chromose's in the range of 0.0 to 1.0
	        );

	        final Engine<DoubleGene, Double> engine = Engine
	                .builder(ghost::getFitnessFunctionJenetics, gtf) //Reference to the Jenetics function passed
	                .populationSize(100) // Inital population size set to 100
	                .offspringSelector(new TournamentSelector<>(5)) //Tornument selection involving 5 individuals used
	                .alterers(new Mutator<>(0.15), new MultiPointCrossover<>(0.3)) //Mutation probability of 15% and Crossover probability of 30% 
	                .build(); //Engine built with specified parameters
	        
	        final Genotype<DoubleGene> result = engine.stream() //Best Genotype collected
	        		.limit(limit.bySteadyFitness(10)) //Termination occurs after 10 generations of the same best Genotype
	                .peek(x -> {
	                    System.out.println("Generation: " + x.getGeneration()); //Print generation number
	                    System.out.println("Best Fitness: " + x.getBestFitness()); //Print the current best Genotype
	                })
	                .collect(EvolutionResult.toBestGenotype());
	        
	      //Results collected a list of doubles
	        ArrayList<Double> searchValues = result.getChromosome().stream().map(NumericGene::doubleValue).collect(Collectors.toCollection(ArrayList::new)); 
	        saveWeights(searchValues,"MyPacManWeights2.txt"); //Export best result as a text file with specified name
	    }
	
	   public static final void saveWeights(ArrayList<Double> weights, String fileName) {
		try {
			FileWriter wr = new FileWriter(fileName);
			 for(Double d:weights) {
				  wr.write(d + "/"); 
			   }
			 wr.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("UNABLE TO WRITE WEIGHTS: " + e.getMessage());
		}
	   }
}
