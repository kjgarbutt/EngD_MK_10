package comparators;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import objects.AidLoad;
import sim.util.geo.MasonGeometry;

/**
 * Orders in terms of increasing distance from HQ/Depot
 * 
 * @author KJGarbutt
 *
 */

public class AidLoadRandomComparator implements Comparator<AidLoad> {

	// Get random ward from the Centroid geomoetry
	
	@Override
	public int compare(AidLoad o1, AidLoad o2) {
		int o1_priority = o1.getTargetCommunity().getIntegerAttribute("PRIO_L");
		int o2_priority = o2.getTargetCommunity().getIntegerAttribute("PRIO_L");
		if(o1_priority == o2_priority) return 0;
		else if(o1_priority < o2_priority) return 1;
		else return -1;
	}
}