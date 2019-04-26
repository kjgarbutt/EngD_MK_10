package sim;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import comparators.AidLoadOSVIComparator;
import ec.util.MersenneTwisterFast;
import objects.AidLoad;
import objects.Driver;
import objects.Headquarters;
import sim.engine.SimState;
import sim.field.geo.GeomVectorField;
import sim.field.network.Edge;
import sim.field.network.Network;
import sim.io.geo.ShapeFileImporter;
import sim.util.Bag;
import sim.util.geo.AttributeValue;
import sim.util.geo.MasonGeometry;
import swise.agents.communicator.Information;
import swise.objects.NetworkUtilities;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;
import utilities.DriverUtilities;
import utilities.InputCleaning;
import utilities.RoadNetworkUtilities;

/**
 * "MK_10" is the final iteration of my EngD project model.
 * 
 * The model is adapted from the MASON demo, "Gridlock", made by Sarah Wise,
 * Mark Coletti, and Andrew Crooks and "SimpleDrivers," made by Sarah Wise.
 * 
 * The model is an example of a simple ABM framework to explore delivering goods
 * during a flood. The model reads a number of GIS shapefiles and displays a
 * road network, two Environment Agency flood maps and a bespoke Open Source
 * Vulnerability Index (OSVI). The model reads in a .CSV and generates a
 * predetermined number of agents with set characteristics. The agents are
 * placed on the road network and are located at a Red Cross office. The model
 * reads a separate .CSV and assigns goal locations to each agent at random from
 * a predetermined list. The agents are assigned speeds at random. Once the
 * model is started, the agents move from A to B, then they change direction and
 * head back to their start position. The process repeats until the user quits.
 *
 * The temporal granularity of the simulation: one tick is 5 minutes
 *
 * @author KJGarbutt
 *
 */
public class EngD_MK_10 extends SimState {

	////////////////////////////////////////////////
	/////////////// MODEL PARAMETERS ///////////////
	////////////////////////////////////////////////
	private static final long serialVersionUID = 1L;
	public static int grid_width = 970;
	public static int grid_height = 620;
	public static double resolution = 5; // the granularity of the simulation-fiddle with this to merge nodes

	public static double speed_vehicle = 5000;	// approximately 60kph/37mph

	public static int loadingTime = 10; // 1 = 5 minutes
	public static int deliveryTime = 20; // 1 = 5 minutes
	
	///////////// COMMODITY PARAMETERS ////////////////
	//public static int approxManifestSize = 10;	// Sandbags. 6 per household. 60 per load/car
	public static int approxManifestSize = 30;
		// Water+Blanket Combo: 1x 24-pack+3x blankets per household. 30 houses per load.
		// Water+Hygiene Kit Combo: 1x 24-pack+3x Hygiene Kits per household. 30 houses per load. 
		// Water+Cleaning Kit Combo. 1x 24-pack+1x Cleaning Kit per household. 30 houses per load.
	//public static int approxManifestSize = 50;
		// Water. 1 24-pack per household. 50 houses served per load
		// There are 4 litres in a gallon. People need 1 gallon per day.
		// Average household size = 3. That's 3 gallons or 12 litres per house per day

	public static int numMaxAgents = 6;
	public static int numMaxLoads = 10000;
	public static int numBays = 5;
	public static double probFailedDelivery = .001;
	public double probBreakdown = .0001;
	public int breakdownRecoveryTime = 25;

	/////////////// DATA SOURCES ///////////////
	String dirName = "data/";
	///////////// GLOUCESTERSHIRE //////////////
	//////////// SNAPPED TO REDUCED ////////////
	//////// MUST USE GL REDUCED ROADS /////////
	String dirOSVIFISOnly = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_LOSVIFZ3_Order_FIS_ONLY_SnappedToReduced.shp";
	String dirFISFISOnly = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_LFIS_Order_FIS_ONLY_SnappedToReduced.shp";
	String dirPRIOFISOnly = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_LPRIO_Order_FIS_ONLY_SnappedToGYOReduced.shp";
	String dirRANDFISOnly = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_Random_Order_FIS_ONLY_SnappedToReduced.shp";
	///////// SNAPPED TO GYO REDUCED ///////////
	////// MUST USE GYO REDUCED ROADS //////////
	String dirOSVIFISOnlyFlooded = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_LOSVIFZ3_Order_FIS_ONLY_SnappedToGYOReduced.shp";
	String dirLFISFISOnlyFlooded = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_LFIS_Order_FIS_ONLY_SnappedToGYOReduced.shp";	
	String dirPRIOFISOnlyFlooded = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_LPRIO_Order_FIS_ONLY_SnappedToGYOReduced.shp";
	String dirRANDFISOnlyFlooded = "GL_Centroids_2019_FIS_ONLY/GL_Centroids_2019_Random_Order_FIS_ONLY_SnappedToGYOReduced.shp";
	////////////////// NORFOLK ////////////////
	//////////// SNAPPED TO REDUCED ///////////
	///////// MUST USE NK REDUCED ROADS ///////
	String dirNKOSVIFISOnly = "NK_Centroids_FIS_ONLY/NK_Centroids_LOSVIFZ3_Order_FISONLY_SnappedToReduced.shp";
	String dirNKLFISFISOnly = "NK_Centroids_FIS_ONLY/NK_Centroids_FIS_Order_FISONLY_SnappedToReduced.shp";
	String dirNKPRIOFISOnly = "NK_Centroids_FIS_ONLY/NK_Centroids_LPRIO_Order_FISONLY_SnappedToReduced.shp";
	String dirNKRANDFISOnly = "NK_Centroids_FIS_ONLY/NK_Centroids_LRANDO_Order_FISONLY_SnappedToReduced.shp";
	////////// SNAPPED TO GYO REDUCED //////////
	/////// MUST USE GYO REDUCED ROADS /////////
	String dirNKOSVIFISOnlyFlooded = "NK_Centroids_MovedForFlooding/NK_Centroids_LOSVIFZ3_Order_FISONLY_SnappedToNoFloodedReduced.shp";
	String dirNKLFISFISOnlyFlooded = "NK_Centroids_MovedForFlooding/NK_Centroids_FIS_Order_FISONLY_SnappedToNoFloodedReduced.shp";
	String dirNKPRIOFISOnlyFlooded = "NK_Centroids_MovedForFlooding/NK_Centroids_LPRIO_Order_FISONLY_SnappedToNoFloodedReduced.shp";
	String dirNKRANDFISOnlyFlooded = "NK_Centroids_MovedForFlooding/NK_Centroids_LRANDO_Order_FISONLY_SnappedToNoFloodedReduced.shp";
	
	/////////////// CONTAINERS ///////////////
	public GeomVectorField world = new GeomVectorField();
	public GeomVectorField baseLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField osviLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField boundaryLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField fz2Layer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField fz3Layer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField roadLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField depotLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField centroidsLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField deliveryLocationLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField agentLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField networkLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField networkEdgeLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField majorRoadNodesLayer = new GeomVectorField(grid_width, grid_height);

	public Bag roadNodes = new Bag();
	public Network roads = new Network(false);

	/////////////// OBJECTS ///////////////
	// Model ArrayLists for agents and OSVI Polygons
	public ArrayList<Driver> agents = new ArrayList<Driver>(10);
	ArrayList<Integer> assignedWards = new ArrayList<Integer>();
	public HashMap<MasonGeometry, Integer> visitedWardRecord = new HashMap<MasonGeometry, Integer>();
	ArrayList<AidLoad> loadsRecord = new ArrayList<AidLoad>();

	ArrayList<Polygon> polys = new ArrayList<Polygon>();
	ArrayList<String> csvData = new ArrayList<String>();
	ArrayList<ArrayList<AidLoad>> rounds;
	public ArrayList<AidLoad> history;

	public GeometryFactory fa = new GeometryFactory();

	long mySeed = 0;

	Envelope MBR = null;

	boolean verbose = false;

	///////////////////////////////////////////////
	/////////////// BEGIN functions ///////////////
	///////////////////////////////////////////////
	/**
	 * Default Constructor
	 * 
	 * @param randomSeed
	 */
	public EngD_MK_10(long randomSeed) {
		super(randomSeed);
		random = new MersenneTwisterFast(12345);
	}

	/**
	 * OSVI Polygon Setup
	 */
	void setup() {
		// copy over the geometries into a list of Polygons
		Bag ps = world.getGeometries();
		polys.addAll(ps);
	}

	/**
	 * Read in data and set up the simulation
	 */
	public void start() {
		super.start();

		System.out.println();
		System.out.println("////////////////\nINPUTTING STUFFS\n////////////////");
		System.out.println();

		try {

			//////////////////////////////////////////////
			///////////// READING IN DATA ////////////////
			//////////////////////////////////////////////

			/////////////////////////////////////////////
			//////////////// LSOA POLYGON ///////////////
			/////////////////////////////////////////////
			File wardsFile = new File("data/GL_OSVI/GL_OSVI_2019.shp");	// Gloucestershire LSOA
			//File wardsFile = new File("data/NK_OSVI/NK_LSOA.shp");	// Norfolk LSOA
			ShapeFileImporter.read(wardsFile.toURI().toURL(), world, Polygon.class);
			System.out.println("Reading in OSVI shapefile from " + wardsFile + "...done");
			// GeomVectorFieldPortrayal polyPortrayal = new GeomVectorFieldPortrayal(true); // for OSVI viz.
			//File wardsFile = new File("data/GL_OSVI_2019.shp");
			
			/////////////////////////////////////////////
			/////// CENTROIDS / DELIVERY GOALS //////////
			/////////////////////////////////////////////
			InputCleaning.readInVectorLayer(centroidsLayer, dirName + 
					dirOSVIFISOnlyFlooded, "All Centroids", new Bag()); 
					//"GL_Centroids_2019_MovedForFlooding/GL_Centroids_2019_MovedForFlooding_LFIS_Order_FIS_ONLY_SnappedToGYOReduced.shp", "All Centroids", new Bag()); 
					//"NK_Centroids/NKCentroidTest.shp", "All Centroids", new Bag()); 
			
			/////////////////////////////////////////////
			/////////////// HQ / DEPOTS /////////////////
			/////////////////////////////////////////////
			GeomVectorField dummyDepotLayer = new GeomVectorField(grid_width, grid_height);
			InputCleaning.readInVectorLayer(dummyDepotLayer, dirName + "GL_HQ/GL_BRC_HQ_ReducedBays.shp", "REDUCED BAYS", new Bag()); // GL 1x Depots
			//InputCleaning.readInVectorLayer(dummyDepotLayer, dirName + "GL_HQ/GL_BRC_HQ.shp", "1x Depot", new Bag()); // GL 1x Depots
			//////InputCleaning.readInVectorLayer(depotLayer, dirName + "GL_HQ/GL_BRC_HQ.shp", "1x Depot", new Bag());	 // GL 1x Depots
			
			//InputCleaning.readInVectorLayer(dummyDepotLayer, dirName + "NK_HQ/NK_BRC_HQ.shp", "1x Depot", new Bag()); // Norfolk 1x Depots	
			//InputCleaning.readInVectorLayer(dummyDepotLayer, dirName + "NK_HQ/NK_BRC_HQ_SnappedToNoFloodedReduced.shp", "1x Depot", new Bag()); // Norfolk 1x Depots
			//////InputCleaning.readInVectorLayer(depotLayer, dirName + "NK_HQ/NK_BRC_HQ_SnappedToNoFloodedReduced.shp", "1x Depot", new Bag()); // Norfolk 1x Depots
			
			///////////// 2x DEPOTS //////////////////
			//InputCleaning.readInVectorLayer(dummyDepotLayer, dirName + "GL_HQ/GL_BRC_HQ_2_SnappedToGYOReduced.shp", "2x Depots", new Bag());	// Gloucestershire 2x Depots
			//////InputCleaning.readInVectorLayer(depotLayer, dirName + "GL_HQ/GL_BRC_HQ_2_SnappedToGYO.shp", "2x Depots", new Bag());	// Gloucestershire 2x Depots
			
			//InputCleaning.readInVectorLayer(dummyDepotLayer, dirName + "NK_HQ/NK_BRC_HQ_2.shp", "2x Depots, Full Roads", new Bag());	//Norfolk 2x Depots
			//InputCleaning.readInVectorLayer(dummyDepotLayer, dirName + "NK_HQ/NK_BRC_HQ_2_SnappedToNoFloodedReduced.shp", "2x Depots, GYO Roads Only", new Bag());	//Norfolk 2x Depots
			//////InputCleaning.readInVectorLayer(depotLayer, dirName + "NK_HQ/NK_BRC_HQ_2_SnappedToNoFloodedReduced.shp", "2x Depots", new Bag());	// Norfolk 2x Depots
		
			//////////////////////////////////////////////
			///////////// FULL ROAD NETWORK //////////////
			//////////////////////////////////////////////
			//InputCleaning.readInVectorLayer(roadLayer, dirName +
			//"GL_Roads/GL_Roads_Reduced.shp", "Full, Non-Flooded Road Network", new Bag());	// Gloucestershire Roads
			//"NK_Roads/NK_Roads_Reduced.shp", "Full, Non-Flooded Road Network", new Bag());	// Norfolk Roads 
			
			//////////////////////////////////////////////
			/////////// FLODDED ROAD NETWORK /////////////
			//////////////////////////////////////////////
			InputCleaning.readInVectorLayer(roadLayer, dirName + 
			"GL_Roads/GL_Roads_GYO_2019_Reduced.shp", "Flooded Road Network - Levels 1-3 Only", new Bag()); // NO MAJOR FLOODED ROADS
			//"NK_Roads/NK_Roads_NoFlooded_Reduced1.shp",	"Flooded Road Network - Levels 1-3 Only", new Bag()); // NO MAJOR FLOODED ROADS

			//////////////////////////////////////////////
			////////////////// BASELAYERS ////////////////
			//////////////////////////////////////////////
			InputCleaning.readInVectorLayer(osviLayer, dirName + "GL_OSVI/GL_OSVI_2019.shp", "OSVI", new Bag());
			//InputCleaning.readInVectorLayer(osviLayer, dirName + "NK_OSVI/NK_LSOA.shp", "OSVI", new Bag());
			InputCleaning.readInVectorLayer(boundaryLayer, dirName + "GL_Boundary/GL_Boundary_Line.shp",
			//InputCleaning.readInVectorLayer(boundaryLayer, dirName + "NK_Boundary/NK_Boundary1.shp",
					"County Boundary", new Bag());	// County Boundary
			
			//////////////////////////////////////////////
			/////////////////// FLOODS ///////////////////
			//////////////////////////////////////////////			
			InputCleaning.readInVectorLayer(fz2Layer, dirName + "GL_FZ/GL_FZ_2.shp", "Flood Zone 2", new Bag());	// Gloucestershire FZ2
			InputCleaning.readInVectorLayer(fz3Layer, dirName + "GL_FZ/GL_FZ_3.shp", "Flood Zone 3", new Bag());	// Gloucestershire FZ3
			//InputCleaning.readInVectorLayer(fz2Layer, dirName + "NK_FZ/NK_FZ2.shp", "Flood Zone 2", new Bag());	// Norfolk FZ2
			//InputCleaning.readInVectorLayer(fz3Layer, dirName + "NK_FZ/NK_FZ3.shp", "Flood Zone 3", new Bag());	// Norfolk FZ3

			////////////////////////////////////////////////
			////////////////// DATA CLEANUP ////////////////
			////////////////////////////////////////////////
			// standardize the MBRs so that the visualization lines up

			MBR = osviLayer.getMBR();
			/////////////// MUST BE ON FOR GL ///////////////////
			MBR.init(340995, 438179, 185088, 247204);
			/////////////// MUST BE ON FOR NK ///////////////////
			//MBR.init(544000, 655000, 348000, 277000);

			// System.out.println("Setting up OSVI Portrayals...");
			// System.out.println();

			setup();

			// clean up the road network
			System.out.println("\nCleaning the road network...");

			roads = NetworkUtilities.multipartNetworkCleanup(roadLayer, roadNodes, resolution, fa, random, 0);
			roadNodes = roads.getAllNodes();
			RoadNetworkUtilities.testNetworkForIssues(roads);

			// set up roads as being "open" and assemble the list of potential termini
			roadLayer = new GeomVectorField(grid_width, grid_height);
			for (Object o : roadNodes) {
				GeoNode n = (GeoNode) o;
				networkLayer.addGeometry(n);

				// check all roads out of the nodes
				for (Object ed : roads.getEdgesOut(n)) {

					// set it as being (initially, at least) "open"
					ListEdge edge = (ListEdge) ed;
					((MasonGeometry) edge.info).addStringAttribute("open", "OPEN");
					networkEdgeLayer.addGeometry((MasonGeometry) edge.info);
					roadLayer.addGeometry((MasonGeometry) edge.info);
					((MasonGeometry) edge.info).addAttribute("ListEdge", edge);
				}
			}

			Network majorRoads = RoadNetworkUtilities.extractMajorRoads(roads);
			RoadNetworkUtilities.testNetworkForIssues(majorRoads);

			// assemble list of secondary versus local roads
			ArrayList<Edge> myEdges = new ArrayList<Edge>();
			GeomVectorField secondaryRoadsLayer = new GeomVectorField(grid_width, grid_height);
			GeomVectorField localRoadsLayer = new GeomVectorField(grid_width, grid_height);
			for (Object o : majorRoads.allNodes) {

				majorRoadNodesLayer.addGeometry((GeoNode) o);

				for (Object e : roads.getEdges(o, null)) {
					Edge ed = (Edge) e;

					myEdges.add(ed);

					String type = ((MasonGeometry) ed.getInfo()).getStringAttribute("class");
					if (type.equals("Not Classified"))
						secondaryRoadsLayer.addGeometry((MasonGeometry) ed.getInfo());
					else if (type.equals("Unclassified"))
						localRoadsLayer.addGeometry((MasonGeometry) ed.getInfo());
				}
			}

			System.gc();

			// set up depots
			setupDepots(dummyDepotLayer);

			// reset MBRs in case they got messed up during all the manipulation
			world.setMBR(MBR);
			centroidsLayer.setMBR(MBR);
			roadLayer.setMBR(MBR);
			networkLayer.setMBR(MBR);
			networkEdgeLayer.setMBR(MBR);
			majorRoadNodesLayer.setMBR(MBR);
			deliveryLocationLayer.setMBR(MBR);
			agentLayer.setMBR(MBR);
			fz2Layer.setMBR(MBR);
			fz3Layer.setMBR(MBR);
			osviLayer.setMBR(MBR);
			baseLayer.setMBR(MBR);
			boundaryLayer.setMBR(MBR);
			depotLayer.setMBR(MBR);

			// System.out.print("done");

			//////////////////////////////////////////////
			////////////////// AGENTS ////////////////////
			//////////////////////////////////////////////
			
			//Way to generate HQs first, then loads
			/*
			for (Object o : depotLayer.getGeometries()) {
				Headquarters d = (Headquarters) o;
				getMostVulnerableUnassignedWard();
				generateLoads(d);
				d.generateRounds();
			}
			*/
			
			//Way to generate loads first and then assign them to depots
			generateLoads();

			agents.addAll(DriverUtilities.setupDriversAtDepots(this, fa, numMaxAgents));
			System.out.println("Prioritising unassigned LSOA...");
			for (Driver p : agents) {
				agentLayer.addGeometry(p);
				getMostVulnerableUnassignedWard();
			}
			// seed the simulation randomly
			seedRandom(System.currentTimeMillis());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setupDepots(GeomVectorField dummyDepots) {
		Bag depots = dummyDepots.getGeometries();
		System.out.println("Setting up Distribution Centre(s)...");

		for (Object o : depots) {
			MasonGeometry mg = (MasonGeometry) o;
			// int numbays = mg.getIntegerAttribute("loadbays");
			GeoNode gn = snapPointToNode(mg.geometry.getCoordinate());

			Headquarters d = new Headquarters(gn.geometry.getCoordinate(), numBays, this);
			//System.out.println("\tHQ located: " + this.hqID + " and has " + numBays + " loading bays.");
			d.setNode(gn);
			
			d.generateRounds();

			depotLayer.addGeometry(d);
			schedule.scheduleOnce(d);
		}
	}

	public Coordinate snapPointToRoadNetwork(Coordinate c) {
		ListEdge myEdge = null;
		double resolution = this.resolution;

		if (networkEdgeLayer.getGeometries().size() == 0)
			return null;

		while (myEdge == null && resolution < Double.MAX_VALUE) {
			myEdge = RoadNetworkUtilities.getClosestEdge(c, resolution, networkEdgeLayer, fa);
			resolution *= 10;
		}
		if (resolution == Double.MAX_VALUE)
			return null;

		LengthIndexedLine closestLine = new LengthIndexedLine(
				(LineString) (((MasonGeometry) myEdge.info).getGeometry()));
		double myIndex = closestLine.indexOf(c);
		return closestLine.extractPoint(myIndex);
	}

	public GeoNode snapPointToNode(Coordinate c) {
		ListEdge myEdge = null;
		double resolution = this.resolution;

		if (networkEdgeLayer.getGeometries().size() == 0)
			return null;

		while (myEdge == null && resolution < Double.MAX_VALUE) {
			myEdge = RoadNetworkUtilities.getClosestEdge(c, resolution, networkEdgeLayer, fa);
			resolution *= 10;
		}
		if (resolution == Double.MAX_VALUE)
			return null;

		double distFrom = c.distance(((GeoNode) myEdge.from()).geometry.getCoordinate()),
				distTo = c.distance(((GeoNode) myEdge.to()).geometry.getCoordinate());
		if (distFrom <= distTo)
			return (GeoNode) myEdge.from();
		else
			return (GeoNode) myEdge.to();
	}

	public static ListEdge getClosestEdge(Coordinate c, double resolution, GeomVectorField networkEdgeLayer,
			GeometryFactory fa) {

		// find the set of all edges within *resolution* of the given point
		Bag objects = networkEdgeLayer.getObjectsWithinDistance(fa.createPoint(c), resolution);
		if (objects == null || networkEdgeLayer.getGeometries().size() <= 0)
			return null; // problem with the network edge layer

		Point point = fa.createPoint(c);

		// find the closest edge among the set of edges
		double bestDist = resolution;
		ListEdge bestEdge = null;
		for (Object o : objects) {
			double dist = ((MasonGeometry) o).getGeometry().distance(point);
			if (dist < bestDist) {
				bestDist = dist;
				bestEdge = (ListEdge) ((AttributeValue) ((MasonGeometry) o).getAttribute("ListEdge")).getValue();
			}
		}

		// if it exists, return it
		if (bestEdge != null)
			return bestEdge;

		// otherwise return failure
		else
			return null;
	}

	public void generateLoads(Headquarters d) {
		System.out.println("Generating parcels...");
		// System.out.print("done");

		ArrayList<AidLoad> myLoads = new ArrayList<AidLoad>();
		Bag centroidGeoms = centroidsLayer.getGeometries();

		System.out.println("Assigning parcels to drivers...");

		for (Object o : centroidGeoms) {

			MasonGeometry myCentroid = (MasonGeometry) o;
			int households = myCentroid.getIntegerAttribute("FZHouses1");

			// create a number of loads based on the number of households
			double numLoads = households / approxManifestSize;
			System.out.println(o + "= " + numLoads);
			for (int i = 0; i < numLoads; i++) {

				Point deliveryLoc = myCentroid.geometry.getCentroid();
				Coordinate myCoordinate = deliveryLoc.getCoordinate();

				if (!MBR.contains(myCoordinate)) {
					System.out.println("myCoordinate is NOT in MBR!");
					i--;
					continue;
				}

				AidLoad p = new AidLoad(d, myCentroid, this);
				p.setDeliveryLocation(myCoordinate);
				myLoads.add(p);

				loadsRecord.add(p);
			}
		}
	}
	
	public void generateLoads() {
		System.out.println("Generating parcels...");
		// System.out.print("done");
		
		Bag centroidGeoms = centroidsLayer.getGeometries();

		//System.out.println("Assigning parcels to drivers...");

		for (Object o : centroidGeoms) {

			MasonGeometry myCentroid = (MasonGeometry) o;
			int households = myCentroid.getIntegerAttribute("FZHouses1");
			Headquarters d = getClosestDepot(myCentroid);
			ArrayList<AidLoad> myLoads = new ArrayList<AidLoad>();

			// create a number of loads based on the number of households + 1 to cover any
			// stragglers
			int numLoads = households / approxManifestSize + 1;
			for (int i = 0; i < numLoads; i++) {

				Point deliveryLoc = myCentroid.geometry.getCentroid();
				Coordinate myCoordinate = deliveryLoc.getCoordinate();

				if (!MBR.contains(myCoordinate)) {
					System.out.println("myCoordinate is NOT in MBR!");
					i--;
					continue;
				}

				AidLoad p = new AidLoad(d, myCentroid, this);
				p.setDeliveryLocation(myCoordinate);
				myLoads.add(p);

				loadsRecord.add(p);
			}
			//d.addLoads(myLoads);
		}
	}

	public Headquarters getClosestDepot(MasonGeometry target) {
		double minDist = Double.MAX_VALUE;
		Headquarters closestHQ = null;
		for (Object o : this.depotLayer.getGeometries()) {
			Headquarters h = (Headquarters) o;
			h.geometry.distance(target.geometry);
			double dist = h.geometry.distance(target.geometry);
			if (dist < minDist) {
				minDist = dist;
				closestHQ = h;	
			}
		}
		return closestHQ;
	}

	int getMostVulnerableUnassignedWard() {
		// System.out.println("\nGetting unassigned LSOA with highest OSVI ratings...");
		Bag centroidGeoms = centroidsLayer.getGeometries();

		int highestOSVI = -1;
		MasonGeometry myCopy = null;

		for (Iterator it = centroidGeoms.iterator(); it.hasNext();) {
			MasonGeometry masonGeometry = (MasonGeometry) it.next();
			boolean isLast = !it.hasNext(); // does this fix myCopy ending up at null?
			// for (Object o : lsoaGeoms) {
			// MasonGeometry masonGeometry = (MasonGeometry) o;
			int id = masonGeometry.getIntegerAttribute("CentroidID"); // checked the ID column and it’s definitely an Int
			// int osviRating = masonGeometry.getIntegerAttribute("L_GL_OSVI_");
			String lsoaID = masonGeometry.getStringAttribute("LSOA_NAME");
			int tempOSVI = masonGeometry.getIntegerAttribute("LOSVIFZ3");
			//int tempOSVI = masonGeometry.getIntegerAttribute("LPRIO");
			int households = masonGeometry.getIntegerAttribute("FZHouses1"); // would give the num of households for
																				// each LSOA. Use for numParcels.
			Point highestWard = masonGeometry.geometry.getCentroid();
			// System.out.println(lsoaID + " - OSVI rating: " + tempOSVI + ", ID: " + id);
			if (assignedWards.contains(id))
				continue;

			// temp = the attribute in the "L_GL_OSVI_" column (int for each LSOA OSVI)
			if (tempOSVI > highestOSVI) { // if temp is higher than highest
				highestOSVI = tempOSVI; // update highest to temp
				myCopy = masonGeometry; // update myCopy, which is a POLYGON
			}
		}

		if (myCopy == null) {
			System.out.println("ALERT: LSOA layer is null! Panic and scream!");
			return -1; // no ID to find if myCopy is null, so just return a fake value
		}

		int id = myCopy.getIntegerAttribute("CentroidID"); // id changes to the highestOSVI
		assignedWards.add(id); // add ID to the "assignedWards" ArrayList
		//System.out.println("\tHighest OSVI Raiting is: " + myCopy.getIntegerAttribute("NOSVIFZ3") + " for: "
		//		+ myCopy.getStringAttribute("LSOA_NAME") + " (ward ID: " + myCopy.getIntegerAttribute("CentroidID") + ")"
		//		+ " and it has " + myCopy.getIntegerAttribute("FZHouses") + " households that may need assistance.");
		//System.out.println("\t\tCurrent list of most vulnerable unassigned wards: " + assignedWards);

		System.out.println("\t"
				+ myCopy.getStringAttribute("LSOA_NAME") + " (LSOA ID: " 
				+ myCopy.getIntegerAttribute("CentroidID") + ") has a OSVIF rating of " 
				+ myCopy.getIntegerAttribute("LOSVIFZ3") + " and it has " 
				+ myCopy.getIntegerAttribute("FZHouses1") + " households that may need assistance.");
		System.out.println("\t\tCurrent list of high priority unassigned wards: " + assignedWards);
		// Prints out: the ID for the highestOSVI
		return myCopy.getIntegerAttribute("CentroidID"); // TODO: ID instead?
	}

	/**
	 * Finish the simulation and clean up
	 */
	public void finish() {
		super.finish();

		System.out.println();
		System.out.println("Simulation ended by user.");

		System.out.println();
		System.out.println("///////////////////////\nOUTPUTTING STUFFS\n///////////////////////");
		System.out.println();

		try {
			// save the history
			BufferedWriter output = new BufferedWriter(
					new FileWriter(dirName + "GL_BaysTest2_OSVI_FIS_OnlyRoundRecord_" + formatted + "_" + mySeed + ".txt"));

			output.write("ROUND RECORD: " + "# Drivers: " + numMaxAgents + "; " + "# Bays: " + numBays + "; "
					+ "Loading Time: " + loadingTime + "; " + "Delivery Time: " + deliveryTime + "; "
					+ "Manifest Size: " + approxManifestSize + "\nDriver,Duration,Distance,Finish time\n");
			for (Driver a : agents) {
				for (String s : a.getHistory())
					output.write(s + "\n");
			}
			output.close();

			BufferedWriter output1 = new BufferedWriter(
					new FileWriter(dirName + "GL_BaysTest2_OSVI_FIS_OnlyParcelRecord_" + formatted + "_" + mySeed + ".txt"));
			output1.write("PARCEL RECORD: " + "# Drivers: " + numMaxAgents + "; " + "# Bays: " + numBays + "; "
					+ "Loading Time: " + loadingTime + "; " + "Delivery Time: " + deliveryTime + "; "
					+ "Manifest Size: " + approxManifestSize
					+ "\nLoad ID,Delivered to,Delivery time step,Load transferred from,Driver,Departure time step\n");
			// output1.write(
			// "Load ID,Delivered to,Delivery time step,Load transferred
			// from,Driver,Departure time step\\n");

			for (AidLoad al : loadsRecord) {
				output1.write(al.giveName() + "\t");
				String pOutput = "";
				for (int s = al.getHistory().size() - 1; s >= 0; s--)
					pOutput += al.getHistory().get(s);
				output1.write(pOutput + "\n");
			}
			output1.close();

			BufferedWriter output2 = new BufferedWriter(
					new FileWriter(dirName + "GL_BaysTest2_OSVI_FIS_OnlyWardsVisited_" + formatted + "_" + mySeed + ".txt"));
			output2.write("WARDS VISITED: " + "# Drivers: " + numMaxAgents + "; " + "# Bays: " + numBays + "; "
					+ "Loading Time: " + loadingTime + "; " + "Delivery Time: " + deliveryTime + "; "
					+ "Manifest Size: " + approxManifestSize + "\nLSOA,Num. Visits\n");
			// output2.write("LSOA,Num. Visits\\n");

			for (MasonGeometry ward : visitedWardRecord.keySet()) {
				output2.write(ward.getStringAttribute("LSOA_NAME") + "\t" + visitedWardRecord.get(ward) + "\n");
			}

			/*
			 * for (AidParcel d : history) { for (String s : d.getHistory()) output.write(s
			 * + "\n"); }
			 */

			output2.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * RoadClosure structure holds information about a road closure
	 */
	public class RoadClosure extends Information {
		public RoadClosure(Object o, long time, Object source) {
			super(o, time, source, 5);
		}
	}

	/** set the seed of the random number generator */
	void seedRandom(long number) {
		random = new MersenneTwisterFast(number);
		mySeed = number;
	}

	SimpleDateFormat df = new SimpleDateFormat("yyyy-mm-dd");
	String formatted = df.format(new Date());

	/**
	 * Main Function
	 * 
	 * Main function allows simulation to be run in stand-alone, non-GUI mode
	 */
	public static void main(String[] args) {

		if (args.length < 0) {
			System.out.println("///////////////////////\nUSAGE ERROR!\n///////////////////////");
			System.exit(0);
		}

		EngD_MK_10 EngD_MK_10 = new EngD_MK_10(System.currentTimeMillis());

		System.out.println("Loading simulation...");

		EngD_MK_10.start();

		System.out.println("Running simulation...");

		while (!EngD_MK_10.schedule.scheduleComplete() && EngD_MK_10.schedule.getTime() < 144)
		//for (int i = 0; i < 145 * 5; i++) { // 12 hour shift?
			EngD_MK_10.schedule.step(EngD_MK_10);
		//}

		EngD_MK_10.finish();

		System.out.println("...simulation run finished.");

		System.exit(0);
	}
}