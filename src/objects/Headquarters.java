package objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import com.vividsolutions.jts.geom.Coordinate;

import comparators.AidLoadDistanceComparator;
import comparators.AidLoadOSVIComparator;
import comparators.AidLoadPriorityComparator;
import sim.EngD_MK_10;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.geo.MasonGeometry;
import swise.agents.SpatialAgent;
import swise.objects.network.GeoNode;

public class Headquarters extends SpatialAgent implements Burdenable {

	EngD_MK_10 world;

	GeoNode myNode = null;

	ArrayList<AidLoad> loads;
	ArrayList<ArrayList<AidLoad>> rounds; // TODO extend upon this!

	int numBays;
	ArrayList<Driver> inBays;
	ArrayList<Driver> waiting;

	public Headquarters(Coordinate c, int numbays, EngD_MK_10 world) {
		super(c);
		loads = new ArrayList<AidLoad>();
		inBays = new ArrayList<Driver>();
		waiting = new ArrayList<Driver>();
		this.world = world;
		this.numBays = numbays;
		rounds = new ArrayList<ArrayList<AidLoad>>();
	}

	public void setNode(GeoNode node) {
		myNode = node;
	}

	public GeoNode getNode() {
		return myNode;
	}

	@Override
	public void addLoad(AidLoad p) {
		loads.add(p);
	}

	@Override
	public boolean removeLoad(AidLoad p) {
		return loads.remove(p);
	}

	public boolean removeLoads(ArrayList<AidLoad> ps) {
		return loads.removeAll(ps);
	}

	@Override
	public void addLoads(ArrayList<AidLoad> ps) {
		loads.addAll(ps);
	}

	@Override
	public boolean makeTransferTo(Object o, Burdenable b) {
		try {
			if (o instanceof ArrayList) {
				loads.removeAll((ArrayList<AidLoad>) o);
				b.addLoads((ArrayList<AidLoad>) o);
			} else {
				loads.remove((AidLoad) o);
				b.addLoad((AidLoad) o);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public Coordinate getLocation() {
		return geometry.getCoordinate();
	}

	@Override
	public void step(SimState arg0) {
		world.schedule.scheduleOnce(this);
	}

	/**
	 * 
	 * @param d
	 *            - the driver
	 * @return the amount of time before which to activate again. If <0, the Depot
	 *         will activate the Driver when ready.
	 */
	public int enterDepot(Driver d) {

		// System.out.println("Driver: " + d.toString() + " has entered HQ!");
		// System.out.println(d.driverID + " has entered HQ!");

		if (rounds.size() == 0)
			return -1; // finished with everything
		else if (inBays.size() >= numBays) {
			waiting.add(d);
			world.schedule.scheduleOnce(new Steppable() {

				@Override
				public void step(SimState state) {
					if (inBays.size() < numBays) {
						waiting.remove(d);
						enterBay(d);
					} else
						state.schedule.scheduleOnce(this);
				}

			});
		} else {
			enterBay(d);

		}

		return EngD_MK_10.loadingTime;
	}

	ArrayList<AidLoad> getNextRound() {
		if (rounds.size() <= 0)
			return null;
		else
			return rounds.remove(0);
	}

	/////////////////////////////////////////////////////////
	////// THIS IS TO SELECT ROUNDS IN A RANDOM ORDER ///////
	///////// AND IS SUPER LAZY! BUT WORKS! /////////////////
	/////////////////////////////////////////////////////////
	
	// ArrayList<AidLoad> getNextRandomRound() {
	// int n = rounds.size();
	// if (n <= 0)
	// return null;
	// else
	// return rounds.remove(random.nextInt(n));
	// }

	void enterBay(Driver d) {
		inBays.add(d);
		if (rounds.size() <= 0)
			return;

		else
			world.schedule.scheduleOnce(world.schedule.getTime() + world.loadingTime, new Steppable() {

				@Override
				public void step(SimState state) {
					ArrayList<AidLoad> newRound = getNextRound();
					if (newRound == null)
						return; // force it to go back, it’s got nothing to do here!!
					// update record of visits!
					// TODO THIS ASSUMES ONLY ONE LOAD PER VEHICLE, and also assumes you're gonna
					// make it!!
					HashMap<MasonGeometry, Integer> records = ((EngD_MK_10) state).visitedWardRecord;
					MasonGeometry targetWard = newRound.get(0).targetCommunity;
					Integer numVisits = records.get(targetWard);
					if (numVisits == null)
						numVisits = 1;
					else
						numVisits++;
					records.put(targetWard, numVisits);

					for (AidLoad al : newRound)
						al.transfer(d);
					d.updateRound();

					// System.out.println(d.driverID + " has taken on a new consignment: " +
					// newRound.toArray().toString());
					// prints: [Ljava.lang.Object;@1d9fe5c1
					leaveDepot(d);
					d.startRoundClock();
				}

			});
	}

	/**
	 * 
	 * @param d
	 *            the Driver to remove from the Depot
	 */
	public void leaveDepot(Driver d) {
		
		// if the Driver was originally there, remove it
		if (inBays.contains(d)) {
			inBays.remove(d);
			world.schedule.scheduleOnce(d);

			// if there are Drivers waiting in the queue, let the next one move in
			if (waiting.size() > 0) {
				Driver n = waiting.remove(0);
				inBays.add(n);
				world.schedule.scheduleOnce(world.schedule.getTime() + EngD_MK_10.loadingTime, n);
			}
		} else
			System.out.println("Error: driver was never in bay");
	}

	public void addRounds(ArrayList<ArrayList<AidLoad>> rounds) {
		this.rounds = rounds;
	}

	public void generateRounds() {
		// rounds.addAll(HeadquartersUtilities.gridDistribution(loads,
		// world.deliveryLocationLayer, world.approxManifestSize));

		// for each load, create a new object. Future examples can have multiple loads
		// per round!
		this.rounds = new ArrayList<ArrayList<AidLoad>>();

		//////////////////////////////////////////////////////////////////////
		////////////// THIS IS WHERE THE STRATEGIES ARE SELECTED /////////////
		/////////////////////////////////////////////////////////////////////
		
		// Chooses LSOA with highest Priority Resident rating
		// AidLoadPriorityComparator alpc = new AidLoadPriorityComparator();
		// Chooses LSOA with highest OSVI rating
		AidLoadOSVIComparator alpc = new AidLoadOSVIComparator();
		// Chooses closest LSOA to HQ
		// AidLoadDistanceComparator alpc = new AidLoadDistanceComparator(this);
		Collections.sort(loads, alpc); // THIS MUST BE ON FOR ABOVE SCENARIOS
		// Comment out the above and use the following to choose
		// loads in a random order
		// Collections.shuffle(loads);

		for (AidLoad al : loads) {
			ArrayList<AidLoad> dummyLoadWrapper = new ArrayList<AidLoad>();
			dummyLoadWrapper.add(al);
			rounds.add(dummyLoadWrapper);
		}
	}

	public String giveName() {
		return "Depot" + this.geometry.getCentroid().toString();
	}
}