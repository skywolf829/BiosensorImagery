package ProcessingCode;
import processing.core.PApplet;
import processing.core.PFont;
import processing.serial.Serial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.emotiv.Iedk.*;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.*;

public class main extends PApplet {
	public class Tuple<A, B, C, D, E> { 
		  public final double a; 
		  public final double b; 
		  public final double c;
		  public final double d;
		  public final double e;
		  public final ArrayList<Double> list;
		  public Tuple(double a, double b, double c, double d, double e) { 
		    this.a = a; 
		    this.b = b;
		    this.c = c;
		    this.d = d;
		    this.e = e;
		    list = new ArrayList<Double>();
		    list.add(a); list.add(b); list.add(c); list.add(d); list.add(e);
		  } 
		  public double Max() {
			  double max = a;
			  if(b > max) max = b;
			  if(c > max) max = c;
			  if(d > max) max = d;
			  if(e > max) max = e;
			  return max;
		  }
		} 
	//Emotiv Variables
	// Corners of the plotted time series
	int[] plotX1Emotiv; 
	int[] plotX2Emotiv;
	int[] labelXEmotiv;
	int[] plotY1Emotiv;
	int[] plotY2Emotiv;
	int[] labelYEmotiv;
	
	Pointer eEvent;
	Pointer eState;
	IntByReference userID;
	boolean ready;
	int state;
	int numSamplesEmotiv = 200;
	int samplingTimeEmotiv = 100;
	DoubleByReference alpha     = new DoubleByReference(0);
	DoubleByReference low_beta  = new DoubleByReference(0);
	DoubleByReference high_beta = new DoubleByReference(0);
	DoubleByReference gamma     = new DoubleByReference(0);
	DoubleByReference theta     = new DoubleByReference(0);
	Map<Integer, ArrayList<Tuple<Double, Double, Double, Double, Double>>> bandValues = new HashMap<Integer, ArrayList<Tuple<Double, Double, Double, Double, Double>>>();
	int[] colors = {color(255, 0, 0), color(0, 255, 0), color(0, 255, 0), color(255, 140, 0), color(255, 0, 255)};
	
	float emotivMax = 1;
	
	//Pulse variables
	int samplingTime = 20;
	int numSamples = 100;
	int numSamplesRead = 0;
	int cycles = 0;
	float pulseRate, range, numPoints, 
	  lastMax, lastMin, currentMax, currentMin;
	float peaks[];
	float[] inputValues = new float[numSamples];
	float[] inputValuesPrime = new float[numSamples-2];
	float[] inputValuesPrimePrime = new float[numSamples-4];

	float plotX1, plotY1;
	float plotX2, plotY2;
	float labelX, labelY;

	boolean enableEmotiv = true, enableHeartbeat = true;
	
	PFont plotFont;
	Serial port;
	
	public static void main(String[] args) {
		PApplet.main("ProcessingCode.main");
	}
	public void settings() {
    	size(720, 650);
	}
    public void setup(){
    	if(enableEmotiv)
    		EmotivSetup();
    	if(enableHeartbeat)
    		HeartbeatSetup();
    }
    void HeartbeatSetup() {    	
    	 
		// Corners of the plotted time series
		plotX1 = 20; 
		plotX2 = width - 20;
		labelX = 15;
		plotY1 = 50;
		plotY2 = height - 250;
		labelY = height - 200;
		  
		plotFont = createFont("SansSerif", 20);
		textFont(plotFont);
		smooth();
		for(int i = 0; i < Serial.list().length; i++){
			println("Serial " + i + ": " + Serial.list()[i]);
		}
		
		port = new Serial(this, Serial.list()[1], 115200);
		thread("readData");
		// don't generate a serialEvent() unless you get a newline character:
		port.bufferUntil('\n');
		
		
    }
    void EmotivSetup() {
    	Edk.INSTANCE.IEE_EngineDisconnect();
    	int eWidth = (width - 60) / 5;
    	plotX1Emotiv = new int[] {10, 20 + eWidth, 30 + eWidth * 2, 40 + eWidth * 3, 50 + eWidth * 4};
    	plotX2Emotiv = new int[] {10 + eWidth, 2*(10 + eWidth), 3*(10 + eWidth), 4*(10 + eWidth), 5*(10 + eWidth)};
    	plotY1Emotiv = new int[] {height - 125, height - 125, height - 125, height - 125, height - 125};
    	plotY2Emotiv = new int[] {height - 25, height - 25, height - 25, height - 25, height - 25};
    	
        eEvent = Edk.INSTANCE.IEE_EmoEngineEventCreate();
		eState = Edk.INSTANCE.IEE_EmoStateCreate();
		userID = new IntByReference(0);
		ready = false;
		state = 0;
		
		bandValues.put(0, new ArrayList<Tuple<Double, Double, Double, Double, Double>>());
		bandValues.put(1, new ArrayList<Tuple<Double, Double, Double, Double, Double>>());
		bandValues.put(2, new ArrayList<Tuple<Double, Double, Double, Double, Double>>());
		bandValues.put(3, new ArrayList<Tuple<Double, Double, Double, Double, Double>>());
		bandValues.put(4, new ArrayList<Tuple<Double, Double, Double, Double, Double>>());

		if (Edk.INSTANCE.IEE_EngineConnect("Emotiv Systems-5") != EdkErrorCode.EDK_OK
				.ToInt()) {
			System.out.println("Emotiv Engine start up failed.");
			return;
		}

		System.out.println("Start receiving Data!");
		System.out.println("Theta, Alpha, Low_beta, High_beta, Gamma");
		System.out.println("Starting emotiv thread");
		thread("UpdateEmotiv");
    }
    
    void ErrorInHeadset() {
    	System.out.println("Internal error in Emotiv Engine!");
		exit();
    }
    
    public void UpdateEmotiv() {
    	while(true) {
	    	state = Edk.INSTANCE.IEE_EngineGetNextEvent(eEvent);
	
			// New event needs to be handled
			if (state == EdkErrorCode.EDK_OK.ToInt()) {
				int eventType = Edk.INSTANCE.IEE_EmoEngineEventGetType(eEvent);
				Edk.INSTANCE.IEE_EmoEngineEventGetUserId(eEvent, userID);
	
				// Log the EmoState if it has been updated
				if (eventType == Edk.IEE_Event_t.IEE_UserAdded.ToInt())
					if (userID != null) {
						System.out.println("User added");
						ready = true;
					}
			} else if (state != EdkErrorCode.EDK_NO_EVENT.ToInt()) {
				ErrorInHeadset();
			}
	
			if (ready) {			    
                int result = Edk.INSTANCE.IEE_GetAverageBandPowers(userID.getValue(), 3, theta, alpha, low_beta, high_beta, gamma);
                if(result == EdkErrorCode.EDK_OK.ToInt()){
                	
                	System.out.print(theta.getValue() + ", "); 
                	System.out.print(alpha.getValue() + ", ");
                	System.out.print(low_beta.getValue() + ", ");
                	System.out.print(high_beta.getValue() + ", "); 
                	System.out.println(gamma.getValue());     
                	
                	Tuple band0 = new Tuple(theta.getValue(), alpha.getValue(), low_beta.getValue(), high_beta.getValue(), gamma.getValue());
                	bandValues.get(0).add(band0);
                	if(band0.Max() > emotivMax) emotivMax = (float) band0.Max();
                }
                result = Edk.INSTANCE.IEE_GetAverageBandPowers(userID.getValue(), 7, theta, alpha, low_beta, high_beta, gamma);
                if(result == EdkErrorCode.EDK_OK.ToInt()){
                	
                	System.out.print(theta.getValue() + ", "); 
                	System.out.print(alpha.getValue() + ", ");
                	System.out.print(low_beta.getValue() + ", ");
                	System.out.print(high_beta.getValue() + ", "); 
                	System.out.println(gamma.getValue());     
                	
                	Tuple band1 = new Tuple(theta.getValue(), alpha.getValue(), low_beta.getValue(), high_beta.getValue(), gamma.getValue());
                	bandValues.get(1).add(band1);
                	if(band1.Max() > emotivMax) emotivMax = (float) band1.Max();
                }          
                result = Edk.INSTANCE.IEE_GetAverageBandPowers(userID.getValue(), 9, theta, alpha, low_beta, high_beta, gamma);
                if(result == EdkErrorCode.EDK_OK.ToInt()){
                	/*
                	System.out.print(theta.getValue() + ", "); 
                	System.out.print(alpha.getValue() + ", ");
                	System.out.print(low_beta.getValue() + ", ");
                	System.out.print(high_beta.getValue() + ", "); 
                	System.out.println(gamma.getValue());
                	*/
                	Tuple band2 = new Tuple(theta.getValue(), alpha.getValue(), low_beta.getValue(), high_beta.getValue(), gamma.getValue());
                	bandValues.get(2).add(band2);          
                	if(band2.Max() > emotivMax) emotivMax = (float) band2.Max();
                }
                result = Edk.INSTANCE.IEE_GetAverageBandPowers(userID.getValue(), 12, theta, alpha, low_beta, high_beta, gamma);
                if(result == EdkErrorCode.EDK_OK.ToInt()){
                	/*
                	System.out.print(theta.getValue() + ", "); 
                	System.out.print(alpha.getValue() + ", ");
                	System.out.print(low_beta.getValue() + ", ");
                	System.out.print(high_beta.getValue() + ", "); 
                	System.out.println(gamma.getValue());
                	*/
                	Tuple band3 = new Tuple(theta.getValue(), alpha.getValue(), low_beta.getValue(), high_beta.getValue(), gamma.getValue());
                	bandValues.get(3).add(band3);
                	if(band3.Max() > emotivMax) emotivMax = (float) band3.Max();
                }
                result = Edk.INSTANCE.IEE_GetAverageBandPowers(userID.getValue(), 16, theta, alpha, low_beta, high_beta, gamma);
                if(result == EdkErrorCode.EDK_OK.ToInt()){
                	/*
                	System.out.print(theta.getValue() + ", "); 
                	System.out.print(alpha.getValue() + ", ");
                	System.out.print(low_beta.getValue() + ", ");
                	System.out.print(high_beta.getValue() + ", "); 
                	System.out.println(gamma.getValue());
                	*/
                	Tuple band4 = new Tuple(theta.getValue(), alpha.getValue(), low_beta.getValue(), high_beta.getValue(), gamma.getValue());
                	bandValues.get(4).add(band4);
                	if(band4.Max() > emotivMax) emotivMax = (float) band4.Max();
                }
                for(int i = 0; i < bandValues.size(); i++) {
                	while(bandValues.get(i).size() > numSamplesEmotiv) {
                		bandValues.get(i).remove(0);
                	}
                }
			}
			try {
				Thread.sleep(samplingTimeEmotiv);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
    public void readData(){  
    	  numSamplesRead = 0;
    	  while(true){
    	    if(port.available() > 0){
    	      String inString = port.readStringUntil('\n');
    	      if (inString != null && inString != "") {
    	        inString = trim(inString);
    	        float inByte = 0;
    	        try{
    	        	inByte = Float.parseFloat(inString);
    	        }catch(Exception e) {
    	        	System.out.println("error reading data");
    	        	continue;
    	        }
    	        inputValues[numSamplesRead % numSamples] = inByte;
    	        
    	        if(numSamplesRead % numSamples >= 2){
    	         inputValuesPrime[(numSamplesRead % numSamples) - 2] = 
    	             (inputValues[(numSamplesRead % numSamples)] - 
    	               inputValues[(numSamplesRead % numSamples) - 2]) / (samplingTime / 1 * 2); 
    	        }
    	        if(numSamplesRead % numSamples >= 4){
    	          inputValuesPrimePrime[(numSamplesRead % numSamples) - 4] =
    	            (inputValuesPrime[(numSamplesRead % numSamples) - 2] - 
    	              inputValuesPrime[(numSamplesRead % numSamples) - 4]) / (samplingTime / 1 * 2);
    	        }
    	        
    	        numSamplesRead++;
    	        if(inByte > currentMax) {
    	          currentMax = inByte;
    	          range = currentMax - currentMin;
    	        }
    	        if(inByte < currentMin){
    	          currentMin = inByte;
    	          range = currentMax - currentMin;
    	        }
    	        if(cycles * numSamples < numSamplesRead){
    	          cycles++;
    	          lastMax = currentMax;
    	          lastMin = currentMin;
    	          currentMax = 0;
    	          currentMin = 0;
    	        }
    	        if(numSamplesRead % numSamples == 0){
    	         FindHeartRate(); 
    	        }
    	      }
    	    }
    	    else{
    	      delay(20);
    	    }
    	  }
    	}


    public void draw(){
    	background(224);
    	
    	if(enableHeartbeat){
    		ColorBackground();
    		DrawPlot();
    	}
    	if(enableEmotiv)
    		DrawPlotsEmotiv();
    	if(enableHeartbeat)
    		DrawDataCurve(); 
    	if(enableEmotiv)
    		DrawDataCurvesEmotiv();
    }
    
    void FindHeartRate(){
    	if(numSamplesRead % numSamples < 2){
    		float[] average = new float[inputValues.length / 2];
    	    int j = 0;
    	    for(int i = 1; i < inputValues.length - 1; i+=2){
    	    	average[j] = (float) ((inputValues[i - 1] + inputValues[i] + inputValues[i + 1]) / 3.0);
    	    	j++;
    	    }
    	    int maxes = 0;
    	    int spot = 0;
    	    boolean findMaxNext = inputValuesPrime[0] > 0;
    	    int[] times = new int[10];
    	    while(spot != -1){
    	    	if(findMaxNext){
    	    		spot = FindNextPeak(spot, average);
    	    		if(spot > 1 && spot != -1) {
    	    			if(maxes < 10) times[maxes] = spot;          
    	    				maxes++;
    	    		}
    	    	}
    	    	else{
    	    		spot = FindNextMin(spot, average);
    	    	}
    	    	findMaxNext = !findMaxNext;
    	    }
    	    println(maxes);
    	    /*
    	    float bpm;
    	    int first, last = 1;
    	    first = times[0] * 2 * samplingTime;
    	    last = times[maxes] * 2 * samplingTime;
    	    if(last != first);
    	      bpm = (maxes / (last - first)) * 60;
    	    println(bpm);
    	    */
    	}
    }
	int FindNextPeak(int start, float[] values){
	  int peak = start + 1;
	  boolean found = false;
	  if(peak >= values.length) return -1;
	  float max = values[peak];
	  int i = start+1;
	  for(i = start; i < values.length && !found; i++){
	     if(values[i] < max){
	       found = true;
	     }
	     else if(values[i] > lastMax * 0.75){
	       max = values[i];
	       peak = i;
	     }
	  }  
	  if(i >= values.length) return -1;
	  return peak;
	}
	int FindNextMin(int start, float[] values){
	  int min = start + 1;
	  boolean found = false;
	  float value = values[min];
	  int i = start+1;
	  for(i = start; i < values.length && !found; i++){
	     if(values[i] > value){
	       found = true;
	     }
	     else if(values[i] < lastMin * 1.4){
	      value = values[i];
	      min = i;
	     }
	  }  
	  if(i >= values.length) return -1;
	  return min;
	}
	void ColorBackground(){
	  if(numSamplesRead == 0) return;
	  int from = color(0, 0, 140);
	  int to = color(140, 0, 0);
	  int currentColor = lerpColor(from, to, 
	        inputValues[(numSamplesRead - 1) % numSamples] / (lastMax > currentMax ? lastMax : currentMax));
	  background(currentColor);
	}
	void DrawPlot(){
	  // The plot will be a white box
	  fill(255);
	  rectMode(CORNERS);
	  noStroke();
	  rect(plotX1, plotY1, plotX2, plotY2);
	  
	  // Draw Title
	  fill(0);
	  textSize(20);
	  textAlign(LEFT);
	  String title = "Skypulse";
	  text(title, (plotX1 + plotY2) / 2, plotY1 - 10);
	  
	  // Draw Axis Labels
	  fill(0);
	  textSize(16);
	  textLeading(15);
	  textAlign(CENTER);
	  text("Samples ("+nfc(samplingTime, 0)+" ms)", (plotX1+plotX2)/2, labelY);
	  
	  pushMatrix();
	  translate(labelX,(plotY1 + plotY2) / 2);
	  rotate(-HALF_PI);
	  text("Reading",0,0);
	  popMatrix(); 
	  textSize(12);
	  text((int)(currentMax > lastMax ? currentMax : lastMax),labelX + 10, plotY1 - 10);
	  
	  // Draw Sample Labels
	  fill(0);
	  textSize(14);
	  textAlign(CENTER);
	  
	  // Use thin, gray lines to draw the grid
	  stroke(224);
	  strokeWeight(1);
	  
	  for (int row = 0; row <= numSamples; row++) {
	    if (row % 100 == 0) {
	      float x = map(row, 0, numSamples + 1, plotX1, plotX2);
	      text(row, x, plotY2 + textAscent() + 10);
	      line(x, plotY1, x, plotY2);
	    }
	  }	  
	}
	void DrawDataCurve() {
	  noFill();
	  beginShape();
	   for (int row = 0; row < (numSamplesRead - 1) % numSamples; row++) {
	      float value = inputValues[row];
	      float x, y;
	      x = plotX1 + ((row / (float)numSamples) * (plotX2 - plotX1));
	      y = plotY2 - ((value / (lastMax > currentMax ? lastMax : currentMax)) * (plotY2 - plotY1 - 20));
	      stroke(0, 102, 153);
	      curveVertex(x, y);
	      if(row == ((numSamplesRead - 1) % numSamples) - 1){
	    	  text(value + "", x, y);
	      }
	   }  
	  
	  endShape();
	}
	void DrawPlotsEmotiv() {
	  
	  for(int i = 0; i < 5; i++) {
		  fill(255);
		  rectMode(CORNERS);
		  noStroke();
		  rect(plotX1Emotiv[i], plotY1Emotiv[i], plotX2Emotiv[i], plotY2Emotiv[i]);
		// Draw Title
		  fill(0);
		  textSize(10);
		  textAlign(LEFT);
		  String title = "Sensor "+ i;
		  text(title, plotX1Emotiv[i], plotY1Emotiv[i] - 5);
		
	  }	 		
	}
	void DrawDataCurvesEmotiv() {
		// Node i
		for(int i = 0; i < 5; i++) {
			//band j out of 5
			for(int j = 0; j < 5; j++) {
				noFill();
				beginShape();
				for(int sample = 0; sample < bandValues.get(i).size(); sample++) {
					float x, y;
					x = plotX1Emotiv[i] + 
							((numSamplesEmotiv - bandValues.get(i).size() + sample) / (float)numSamplesEmotiv) * (plotX2Emotiv[i] - plotX1Emotiv[i]);
					y = (float) (plotY2Emotiv[i] - (double)(bandValues.get(i).get(sample).list.get(j) / emotivMax)
							* (plotY2Emotiv[i] - plotY1Emotiv[i]));
							
					stroke(colors[j]);
					
					curveVertex(x, y);
				}
				endShape();
			}
		}
	}
}
