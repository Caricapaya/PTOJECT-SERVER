import java.util.*;
import org.ini4j.*;
import java.io.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

//This class reads from an xml file and manages designated areas, such as "campus" or different buildings
class DesignatedAreaManager{
	Wini iniFile;
	File areaFile;

	DesignatedArea campusArea = null;
	ArrayList<DesignatedArea> designatedAreas;
	public DesignatedAreaManager(){
		try{
			iniFile = new Wini(new File("config.ini"));
			areaFile = new File(iniFile.get("designated areas", "filename"));

			//parse xml file to dom document
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(areaFile);

			//get list of <DesignatedArea> nodes
			NodeList areas = document.getElementsByTagName("DesignatedArea");

			designatedAreas = new ArrayList<DesignatedArea>();

			//parse dom document into designated area objects
			for (int i = 0; i < areas.getLength(); i++) {
				DesignatedArea temp = new DesignatedArea(areas.item(i));
				if (temp.getType() == DesignatedArea.CAMPUS) {
					//there can only be one campus area
					campusArea = temp;
				}
				else{
					designatedAreas.add(temp);
				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
			System.exit(0);
		}
	}

	//see if coordinates fall within campus area
	public boolean isOnCampus(double lat, double lng){
		return campusArea.contains(lat, lng);
	}

	//overload
	public boolean isOnCampus(Device dev){
		return campusArea.contains(dev.getLatitude(), dev.getLongitude());
	}

	//get name of area in which the corrdinates fall
	public String currentArea(double lat, double lng){
		for (DesignatedArea area : designatedAreas) {
			if (area.contains(lat, lng)) {
				return area.getName();
			}
		}
		return null;
	}

	//overload
	public String currentArea(Device dev){
		return currentArea(dev.getLatitude(), dev.getLongitude());
	}
}