import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.Blitter;

import ij.plugin.filter.PlugInFilter;

import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.gui.PolygonRoi;

import java.awt.Rectangle;

import ij.io.FileSaver;

// This plugin takes a segmented line region of interest in an image
// and transforms it into a straight line with a height of 31 pixels
public class Preprocessor_2 implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return PlugInFilter.DOES_ALL;
	}

	public void run(ImageProcessor ip) {
		ip.setInterpolate(true);
		boolean interpolate = PlotWindow.interpolate;
		
		// assume that the user has defined a set of N coordinates
		// from a segmented line
		Roi roi = imp.getRoi();
		int numRoiPts = ((PolygonRoi)roi).getNCoordinates();
		int[] roiXPts = ((PolygonRoi)roi).getXCoordinates();
		int[] roiYPts = ((PolygonRoi)roi).getYCoordinates();
		Rectangle r = roi.getBounds();

		int roiXStart = r.x;
		int roiYStart = r.y;

		double totalRoiLength = 0.0;
		double[] segmentLengths = new double[numRoiPts];
		int[] xSegmentDistances = new int[numRoiPts];
		int[] ySegmentDistances = new int[numRoiPts];

		// compute the distances between each pair of adjacent points
		for (int i = 0; i < numRoiPts - 1; i++) {
			int xDelta = roiXPts[i+1] - roiXPts[i];
			int yDelta = roiYPts[i+1] - roiYPts[i];
			double segmentLength = Math.sqrt(xDelta*xDelta + yDelta*yDelta);

			totalRoiLength += segmentLength;
			segmentLengths[i] = segmentLength;
			xSegmentDistances[i] = xDelta;
			ySegmentDistances[i] = yDelta;
		}

		double[] values = new double[(int) totalRoiLength];

		// abritrarily assigned to be 15.  unknown reason
		int roiHeight = 15;

		int processedImageWidth = (int) totalRoiLength;
		int processedImageHeight = roiHeight * 2 + 1;
		ImageProcessor ip2 = new FloatProcessor(processedImageWidth, processedImageHeight);

		double leftOver = 1.0;
		double distance = 0.0;

		for (int i = 0; i < numRoiPts; i++) {
			double curSegmentLength = segmentLengths[i];
			
			if (curSegmentLength == 0.0){
				continue;
			}

			//To know the distance from point compute angle and theta angle of the vertical line
			double angle = Math.atan2(ySegmentDistances[i], xSegmentDistances[i]);
			double theta = angle - ( Math.PI / 2 );
			double xinc = xSegmentDistances[i] / curSegmentLength;
			double yinc = ySegmentDistances[i] / curSegmentLength;

			double start = 1.0 - leftOver;
			double rx = roiXStart + roiXPts[i] + start * xinc;
			double ry = roiYStart + roiYPts[i] + start * yinc;
			double len2 = curSegmentLength - start;
			int n2 = (int)len2;

			for (int j = 0; j <= n2; j++) {
				int index = (int) distance + j;

				if (index < values.length) {
						for (int k=0; k< processedImageHeight; k++){
							double drx = Math.cos(theta) * (roiHeight - k);
							double dry = Math.sin(theta) * (roiHeight - k);
							double rx2 = rx + drx;
							double ry2 = ry + dry;
							ip2.putPixelValue(index, k, ip.getInterpolatedValue(rx2, ry2));
						}
				}
				rx += xinc;
				ry += yinc;
			}
			distance += curSegmentLength;
			leftOver = len2 - n2;
		}
		
		//smoothing
		ImageProcessor ip3 = ip2.duplicate();
		ip3.medianFilter();
		ip3.smooth();

		//calculation of background: there should be better methods. but This is best with my ability for now.
		//You may just use subtract "background" of ImageJ default function.
		ImageProcessor ip4 = ip3.duplicate();
		int finalLength = ip4.getWidth();
		float[][] floatarray = ip4.getFloatArray();

		//reference row number
		int refs = 5;
		for (int i=0; i < finalLength; i++){
			//diff is linear interpolated difference of each pixel.
			float diff = (floatarray[i][refs - 1] - floatarray[i][processedImageHeight - refs])/(processedImageHeight - refs * 2 + 1);
			for(int j = refs; j < processedImageHeight - refs ;j++){				
				floatarray[i][j] = floatarray[i][j - 1] - diff; 
			}
		}
		ip4.setFloatArray(floatarray);
		
		//subtruct backgound from cropped image.
		ImageProcessor ip5 = ip2.duplicate();
		ip5.copyBits(ip4, 0, 0, Blitter.SUBTRACT);

		//convertToShort(false) clamped without scaling, but sets negative valuse to 0. this is different from octave subtracition method that I wrote before.
		ImageProcessor ip5short = ip5.convertToShort(false);
		ImagePlus im5 = new ImagePlus("subtracted", ip5short);
		im5.show();
		
		//our camera is 12bit, so save the 16 bit data.
		FileSaver fs = new FileSaver(im5);
		fs.saveAsText();
	}

}
