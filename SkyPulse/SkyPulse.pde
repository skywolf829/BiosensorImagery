// Define signal parameters
int samplingTime = 5;
int numSamples = 600;
int numSamplesRead = 0;
int cycles = 0;
float pulseRate, range, numPoints, 
  lastMax, lastMin, currentMax, currentMin;
float peaks[];
float[] inputValues = new float[numSamples];
float[] inputValuesPrime = new float[numSamples-2];
float[] inputValuesPrimePrime = new float[numSamples-4];

// Define display
float plotX1, plotY1;
float plotX2, plotY2;
float labelX, labelY;

PFont plotFont;

import processing.serial.*;
Serial port;

void setup(){
  size(720, 450);
 
  // Corners of the plotted time series
  plotX1 = 20; 
  plotX2 = width - 20;
  labelX = 120;
  plotY1 = 50;
  plotY2 = height - 150;
  labelY = height - 100;
  
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

void readData(){  
  numSamplesRead = 0;
  while(true){
    if(port.available() > 0){
      String inString = port.readStringUntil('\n');
      if (inString != null) {
        inString = trim(inString);
        float inByte = float(inString);
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
      }
    }
    else{
      delay(1);
    }
  }
}

void draw(){
  background(224);
  ColorBackground();
  DrawPlot();
  DrawDataCurve(); 
}

void ColorBackground(){
  color from = color(0, 0, 140, 0.01);
  color to = color(140, 0, 0, 0.01);
  color currentColor = lerpColor(from, to, 
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
  text(title, plotX1, plotY1 - 10);
  
  // Draw Axis Labels
  fill(0);
  textSize(16);
  textLeading(15);
  textAlign(CENTER);
  text("Samples ("+nfc(samplingTime, 0)+" ms)", (plotX1+plotX2)/2, labelY);
  
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
   }  
  
  endShape();
}