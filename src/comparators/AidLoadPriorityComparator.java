package comparators;

import java.util.Comparator;

import objects.AidLoad;

/**
 * Orders in terms of decreasing priority
 * 
 * @author KJGarbutt
 *
 */
public class AidLoadPriorityComparator implements Comparator<AidLoad> {

	@Override
	public int compare(AidLoad o1, AidLoad o2) {
		int o1_priority = o1.getTargetCommunity().getIntegerAttribute("LPRIO");
		int o2_priority = o2.getTargetCommunity().getIntegerAttribute("LPRIO");
		System.out.println("Comparing: " + o1_priority + " + " + o2_priority + "...");
		if (o1_priority == o2_priority)
			return 0;
		else if (o1_priority < o2_priority)
			return 1;
		else
			return -1;
	}

}