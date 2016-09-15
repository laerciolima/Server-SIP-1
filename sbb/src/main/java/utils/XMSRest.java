package utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.algartelecom.meuPrimeiroProjeto.CommonSbb;

public class XMSRest implements Observer {

	String xmsip = null;
	String makecalldest = "sip:softphone@192.168.1.8:5080";
	String playfile = "file://verification/play_menu.wav";
	int port = 81;
	CommonSbb sbb;

	boolean isStreaming = false;
	boolean isDonePlaying = false;

	public XMSRest(String xmsip, int port, CommonSbb commonSbb) {
		super();
		this.xmsip = xmsip;
		this.port = port;
		this.sbb = commonSbb;

		Properties prop = new Properties();

		try {
			// Uncomment this block to generate the config.properties file if
			// none is available
			prop.setProperty("XMSIP", xmsip);
			prop.setProperty("MakecallDestination", makecalldest);
			prop.setProperty("PlayFile", playfile);
			prop.store(new FileOutputStream("config.properties"), null);
			// return;

			// This will load the properties from the config file
			/*
			 * prop.load(XMSRestTest2.class.getResourceAsStream(
			 * "/config.properties")); xmsip = prop.getProperty("XMSIP");
			 * makecalldest = prop.getProperty("MakecallDestination");
			 * playfile=prop.getProperty("PlayFile");
			 */
			System.out.println("Application Properties Set to:\n" + prop);

			XMSEventListener el = new XMSEventListener();

			// Step 2: Have the EventListener connect to the XMS the
			// destination ip address and port are configurable above
			el.ConnectToXMS(xmsip, port);

			// Step 3: Setup this class as an Observer of the EL so that it can
			// be notified when the events come in on the stream. When an
			// event is received the Update Method will be called
			el.addObserver(this);

			// Step 4: Start the EL listening for new events
			 el.StartListening();

		} catch (IOException ex) {
			// If the file is not availabe it will run with default parms above
			System.out.println("config.properties can't be found, running with default properties");
		}

	}

	public void update(Observable o, Object arg) {
		// TODO Auto-generated method stub
		System.out.println("\n\nReceived Event:\n" + arg);
		if (arg.toString().contains("<event_data name=\"type\" value=\"STREAM\" />")) {
			System.out.println("STREAMING event detected, setting isStreaming to true");
			isStreaming = true;
		} else if (arg.toString().contains("<event_data name=\"type\" value=\"END_PLAY\" />")) {
			System.out.println("STREAMING event detected, setting isDonePlaying to true");
			isDonePlaying = true;
			
		}

	}

	public String sendRequestPOST(String path, String req) {

		XMSRestSender sender = new XMSRestSender(xmsip);

		System.out.println(
				"\n\n===========================================================\nMaking outbound call via POST:");

		String xmlresponse = sender.POST(path, req);
		System.out.println("POST XML response\n" + xmlresponse);

		return xmlresponse;
	}

	public String sendRequestPUT(String href, String req) {

		XMSRestSender sender = new XMSRestSender(xmsip);

		System.out.println(
				"\n\n===========================================================\nMaking PUT request href:"+href);

		String xmlresponse = sender.PUT(href, req);
		System.out.println("PUT XML response\n" + xmlresponse);

		return xmlresponse;
	}
	public String sendRequestDELETE(String href) {

		XMSRestSender sender = new XMSRestSender(xmsip);

		System.out.println(
				"\n\n===========================================================\nMaking outbound call via POST:");

		String xmlresponse = sender.DELETE(href);
		System.out.println("DELETE XML response\n" + xmlresponse);

		return xmlresponse;
	}
	
	public String getHrefFromResponse(String xmlresponse) {

		// Here we need to pull out the href or callID
		Pattern pattern = Pattern.compile("href=\"(.*?)\"");
		Matcher matcher = pattern.matcher(xmlresponse);
		String href = "";
		if (matcher.find()) {
			href = matcher.group(1);
			System.out.println("href=" + href);
		} else {
			System.out.println("No href found!");
		}
		return href;
	}
	
	public String getSdpFromResponse(String xmlresponse) {

		// Here we need to pull out the href or callID
		Pattern pattern = Pattern.compile("sdp=\"(.*?)\"");
		Matcher matcher = pattern.matcher(xmlresponse);
		String href = "";
		if (matcher.find()) {
			href = matcher.group(1);
			System.out.println("SDP do response=" + href);
		} else {
			System.out.println("No href found!");
		}
		return href;
	}
	
	public void setSbb(CommonSbb sbb){
		this.sbb = sbb;
	}

}
