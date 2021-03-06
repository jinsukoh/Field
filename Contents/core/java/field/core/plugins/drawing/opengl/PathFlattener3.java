package field.core.plugins.drawing.opengl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import field.core.plugins.drawing.opengl.LineUtils.ClosestPointToSpline3;
import field.math.linalg.Vector3;

public class PathFlattener3 {

	private final CachedLine c;

	public class Mapping {
		public float dotStart; // node.t
		public float dotEnd; // node.t
		public Vector3 start;
		public Vector3 end;
		public float cumulativeDistanceAtEnd;
		
		@Override
		public String toString() {
			return dotStart+"->"+dotEnd+" > "+cumulativeDistanceAtEnd;
		}
	}

	List<Mapping> mappings = new ArrayList<Mapping>();
	private final float tol;

	public PathFlattener3(CachedLine c, float tol) {
		this.c = c;
		this.tol = tol;
		CachedLineCursor cursor = new CachedLineCursor(c);
		int index = 0;

		while (cursor.hasNextSegment()) {
			if (cursor.nextIsCubic()) {
				Vector3 a = new Vector3();
				Vector3 c1 = new Vector3();
				Vector3 c2 = new Vector3();
				Vector3 b = new Vector3();
				cursor.nextCubicFrame3(a, c1, c2, b);
				emitCubicFrame(index - 1, index, a, c1, c2, b);
			} else if (!cursor.nextIsSkip()) {
				Vector3 a = new Vector3();
				Vector3 b = new Vector3();
				cursor.nextLinearFrame3(a, b);
				emitLinearFrame(index - 1, index, a, b);
			}
			index++;
			cursor.next();
		}
	}
	
	public List<Mapping> getMappings() {
		return mappings;
	}

	Comparator searchDistance = new Comparator() {
		public int compare(Object o1, Object o2) {
			float f1 = o1 instanceof Number ? ((Number) o1).floatValue() : ((Mapping) o1).cumulativeDistanceAtEnd;
			float f2 = o2 instanceof Number ? ((Number) o2).floatValue() : ((Mapping) o2).cumulativeDistanceAtEnd;
			return Float.compare(f1, f2);
		}
	};

	Comparator searchDot = new Comparator() {
		public int compare(Object o1, Object o2) {
			float f1 = o1 instanceof Number ? ((Number) o1).floatValue() : ((Mapping) o1).dotEnd;
			float f2 = o2 instanceof Number ? ((Number) o2).floatValue() : ((Mapping) o2).dotEnd;
			return Float.compare(f1, f2);
		}
	};

	public float length() {
		if (mappings.size()<1) return 0;
		return mappings.get(mappings.size() - 1).cumulativeDistanceAtEnd;
	}
	
	public List<Mapping> getMappingSublist(float length)
	{
		int found = Collections.binarySearch((List) mappings, new Float(length), searchDistance);
		if (found >= 0)
			return mappings.subList(0, found+1);
		return mappings.subList(0, -found-1+1);
	}
	

	public float lengthToDot(float length) {
		if (length==0) return 0;
		if (mappings.size()==0) return 0;
		
		int found = Collections.binarySearch((List) mappings, new Float(length), searchDistance);
		if (found >= 0)
			return mappings.get(found).dotEnd;

		int leftOf = -found - 1;
		int rightOf = leftOf - 1;
		if (leftOf > mappings.size() - 1)
			return mappings.get(mappings.size() - 1).dotEnd;

		float l1 = rightOf >= 0 ? mappings.get(rightOf).cumulativeDistanceAtEnd : 0;
		float l2 = mappings.get(leftOf).cumulativeDistanceAtEnd;

		if (l2 == l1)
			return mappings.get(leftOf).dotEnd;
		float x = (length - l1) / (l2 - l1);
		float de = mappings.get(leftOf).dotStart * (1 - x) + x * mappings.get(leftOf).dotEnd;

		return de;
	}

	public float dotToLength(float dot) {
		
//		;//System.out.println(" dot to length <"+dot+">");
//		;//System.out.println(" mappings :"+mappings);
		
		int found = Collections.binarySearch((List) mappings, new Float(dot), searchDot);
//		;//System.out.println(" found <"+found+">");
		if (found >= 0)
			return mappings.get(found).cumulativeDistanceAtEnd;

		int leftOf = -found - 1;
		int rightOf = leftOf - 1;
		
//		;//System.out.println(" left right <"+leftOf+"> <"+rightOf+">");
		
		if (leftOf > mappings.size() - 1)
			return mappings.get(mappings.size() - 1).cumulativeDistanceAtEnd;


		float l1 = mappings.get(leftOf).dotStart;
		float l2 = mappings.get(leftOf).dotEnd;
		
//		;//System.out.println(" left <"+l1+"> <"+l2+">");

		if (l2 == l1)
			return mappings.get(rightOf).cumulativeDistanceAtEnd;
		float x = (dot - l1) / (l2 - l1);
		float de = (rightOf>=0 ? mappings.get(rightOf).cumulativeDistanceAtEnd : 0) * (1 - x) + x * mappings.get(leftOf).cumulativeDistanceAtEnd;

		return de;
	}

	private void emitLinearFrame(float dotStart, float dotEnd, Vector3 a, Vector3 b) {
		Mapping m = new Mapping();
		m.start = a;
		m.end = b;
		m.dotStart = dotStart;
		m.dotEnd = dotEnd;
		if (mappings.size() == 0)
			m.cumulativeDistanceAtEnd = b.distanceFrom(a);
		else
			m.cumulativeDistanceAtEnd = b.distanceFrom(a) + mappings.get(mappings.size() - 1).cumulativeDistanceAtEnd;
		mappings.add(m);
	}

	Vector3 tmp = new Vector3();

	private void emitCubicFrame(float dotStart, float dotEnd, Vector3 a, Vector3 c1, Vector3 c2, Vector3 b) {

		float f = flatnessFor(a, c1, c2, b);
		if (f > tol) {
			Vector3 c12 = new Vector3();
			Vector3 c21 = new Vector3();
			Vector3 m = new Vector3();

			LineUtils.splitCubicFrame3(a, c1 = new Vector3(c1), c2 = new Vector3(c2), b, 0.5f, c12, m, c21, tmp);

			float mp = dotStart + (dotEnd - dotStart) * 0.5f;

			emitCubicFrame(dotStart, mp, a, c1, c12, m);
			emitCubicFrame(mp, dotEnd, m, c21, c2, b);
		} else {
			emitLinearFrame(dotStart, dotEnd, a, b);
		}
	}

	private float flatnessFor(Vector3 a, Vector3 c1, Vector3 c2, Vector3 b) {
	//	return (float) CubicCurve2D.getFlatness(a.x, a.y, c1.x, c1.y, c2.x, c2.y, b.x, b.y);
		float f1 = (float) ClosestPointToSpline3.ptSegDistSq3(a.x, a.y, a.z, b.x, b.y, b.z, c1.x, c1.y, c1.z); 
		float f2 = (float) ClosestPointToSpline3.ptSegDistSq3(a.x, a.y, a.z, b.x, b.y, b.z, c2.x, c2.y, c2.z);
		return (float) Math.sqrt(Math.max(f1, f2));
	}

}
