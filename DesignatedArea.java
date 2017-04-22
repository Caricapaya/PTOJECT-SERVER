import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.lang.Math;
import java.util.*;

import java.io.*;

class DesignatedArea{
	public static final int CAMPUS = 0;
	public static final int BUILDING = 1;
	public static final int AREA = 2;


	String name;
	int type;
	ArrayList<Shape> subAreas;

	public DesignatedArea(Node area){
		subAreas = new ArrayList<Shape>();
		NodeList children = area.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			//set name
			if (child.getNodeName().equals("Name")){
				name = child.getTextContent();
			}

			//get type
			if (child.getNodeName().equals("Type")) {
				if (child.getTextContent().equals("campus")) {
					type = CAMPUS;
				}
				if (child.getTextContent().equals("building")) {
					type = BUILDING;
				}
				if (child.getTextContent().equals("area")) {
					type = AREA;
				}
			}

			//get and load shapes
			if (child.getNodeName().equals("Shapes")) {
				loadShapes(child);
			}

		}
	}

	public String getName(){
		return name;
	}

	public String getTypeName(){
		switch (type) {
			case CAMPUS:
				return "Campus";
			case AREA:
				return "Area";
			case BUILDING:
				return "Building";
		}
		return "";
	}

	public int getType(){
		return type;
	}

	public boolean contains(double lat, double lng){
		for (Shape area : subAreas) {
			if (area.containsPoint(lat, lng)) {
				return true;
			}
		}
		return false;
	}

	private void loadShapes(Node shapes){
		NodeList children = shapes.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeName().equals("Circle")) {
				double radius = 0;
				double lat = 0;
				double lng = 0;

				NodeList shapeChildren = child.getChildNodes();
				for (int j = 0; j < shapeChildren.getLength(); j++) {
					Node shapeChild = shapeChildren.item(j);
					//load radius of circle
					if (shapeChild.getNodeName().equals("Radius")) {
						radius = Double.parseDouble(shapeChild.getTextContent());
					}
					//load center of circle
					if (shapeChild.getNodeName().equals("Center")) {
						NodeList centerChildren = shapeChild.getChildNodes();
						for (int k = 0; k < centerChildren.getLength(); k++) {
							Node centerChild = centerChildren.item(k);
							if (centerChild.getNodeName().equals("Lat")) {
								lat = Double.parseDouble(centerChild.getTextContent());
							}
							if (centerChild.getNodeName().equals("Lng")) {
								lng = Double.parseDouble(centerChild.getTextContent());
							}
						}
					}
				}
				subAreas.add(new Circle(lat, lng, radius));
			}



			if (child.getNodeName().equals("Rectangle")) {
				double north = 0;
				double east = 0;
				double south = 0;
				double west = 0;

				NodeList shapeChildren = child.getChildNodes();
				for (int j = 0; j < shapeChildren.getLength(); j++) {
					Node shapeChild = shapeChildren.item(j);
					//load bounds
					if (shapeChild.getNodeName().equals("Bounds")) {
						NodeList boundsChildren = shapeChild.getChildNodes();
						//load each bound
						for (int k = 0; k < boundsChildren.getLength(); k++) {
							Node boundsChild = boundsChildren.item(k);
							if (boundsChild.getNodeName().equals("North")) {
								north = Double.parseDouble(boundsChild.getTextContent());
							}
							if (boundsChild.getNodeName().equals("East")) {
								east = Double.parseDouble(boundsChild.getTextContent());
							}
							if (boundsChild.getNodeName().equals("South")) {
								south = Double.parseDouble(boundsChild.getTextContent());
							}
							if (boundsChild.getNodeName().equals("West")) {
								west = Double.parseDouble(boundsChild.getTextContent());
							}
						}
					}
				}
				subAreas.add(new Rectangle(north, east, south, west));
			}
		}
	}

	private interface Shape{
		public boolean containsPoint(double lat, double lng);
	}

	private class Circle implements Shape{
		double centerLat;
		double centerLng;
		double radius;

		public Circle(double lat, double lng, double rad){
			centerLat = lat;
			centerLng = lng;
			radius = rad;
		}

		//check if shape contains latitude/longitude point
		public boolean containsPoint(double lat, double lng){
			//find distance from center of circle
			double distance = measure(lat, lng, centerLat, centerLng);
			//check if distance exceeds radius of circle and return
			return distance < radius;
		}

		//from http://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
		private double measure(double lat1, double lon1, double lat2, double lon2){  // generally used geo measurement function
		    double R = 6378.137; // Radius of earth in KM
		    double dLat = lat2 * Math.PI / 180.0 - lat1 * Math.PI / 180.0;
		    double dLon = lon2 * Math.PI / 180.0 - lon1 * Math.PI / 180.0;
		    double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		    Math.cos(lat1 * Math.PI / 180.0) * Math.cos(lat2 * Math.PI / 180.0) *
		    Math.sin(dLon/2) * Math.sin(dLon/2);
		    double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		    double d = R * c;
		    return d * 1000.0; // meters
		}
	}

	//check if shape contains latitude/longitude point
	private class Rectangle implements Shape{
		double boundsNorth;
		double boundsEast;
		double boundsSouth;
		double boundsWest;

		public Rectangle(double north, double east, double south, double west){
			boundsNorth = north;
			boundsEast = east;
			boundsSouth = south;
			boundsWest = west;
		}

		public boolean containsPoint(double lat, double lng){
			boolean isWithin = true;
			//check if point is contained by bounds
			//does not work correctly around the poles or where the longitude sign flips
			isWithin &= lat < boundsNorth;
			isWithin &= lat > boundsSouth;
			isWithin &= lng < boundsEast;
			isWithin &= lng > boundsWest;
			return isWithin;
		}
	}
}