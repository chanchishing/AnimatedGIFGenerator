import javax.imageio.*;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Scanner;


// This class is based on https://memorynotfound.com/generate-gif-image-java-delay-infinite-loop-example/
// with some modification
class GifSequenceWriter {

    protected ImageWriter writer;
    protected ImageWriteParam params;
    protected IIOMetadata metadata;

    public GifSequenceWriter(ImageOutputStream out, int imageType, int delay, boolean loop) throws IOException {
        writer = ImageIO.getImageWritersBySuffix("gif").next();
        params = writer.getDefaultWriteParam();

        ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
        metadata = writer.getDefaultImageMetadata(imageTypeSpecifier, params);

        configureRootMetadata(delay, loop);

        writer.setOutput(out);
        writer.prepareWriteSequence(null);
    }

    private void configureRootMetadata(int delay, boolean loop) throws IIOInvalidTreeException {
        String metaFormatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

        IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
        graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(delay/10));
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode commentsNode = getNode(root, "CommentExtensions");
        commentsNode.setAttribute("CommentExtension", "Created by: https://memorynotfound.com");

        // no need to add the Netscape Application extension if loop is not needed
        // (reference: http://www.vurdalakov.net/misc/gif/netscape-looping-application-extension)
        if (loop) {
            IIOMetadataNode appExtensionsNode = getNode(root, "ApplicationExtensions");
            IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");
            child.setAttribute("applicationID", "NETSCAPE");
            child.setAttribute("authenticationCode", "2.0");

            int loopContinuously = loop ? 0 : 1;
            child.setUserObject(new byte[]{0x1, (byte) (loopContinuously & 0xFF), (byte) ((loopContinuously >> 8) & 0xFF)});
            appExtensionsNode.appendChild(child);
        }

        metadata.setFromTree(metaFormatName, root);
    }

    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName){
        int nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++){
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)){
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return(node);
    }

    public void writeToSequence(RenderedImage img) throws IOException {
        writer.writeToSequence(new IIOImage(img, null, metadata), params);
    }

    public void close() throws IOException {
        writer.endWriteSequence();
    }

}

public class AnimatedGenerator {
    public static void main(String[] args) throws IOException {
        // Prompt the user to enter the input text file name
        // Prompt for delay between frame
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the input text file name: ");
        String inputFileName = scanner.nextLine();
        System.out.print("Enter the delay between frames in milliseconds: ");
        int delay = scanner.nextInt();
        scanner.close();


        // Set the output file name to be the same as the input file name with a .gif extension
        String outputFileName = inputFileName.substring(0, inputFileName.lastIndexOf('.')) + ".gif";

        boolean loop = false;
        File outputFile = new File(outputFileName);
        ImageOutputStream output = new FileImageOutputStream(outputFile);
        GifSequenceWriter writer = new GifSequenceWriter(output, BufferedImage.TYPE_INT_RGB, delay, loop);

        // Set up the font and color for the text
        Font font = new Font("Calibri", Font.PLAIN, 122);
        Color fontColor = new Color(0, 176, 80);

        // Set up the background color for the GIF
        Color backgroundColor = Color.WHITE;

        // Set up the image dimensions and bottom margin
        int width = 1024; // for example
        int height = 120;
        int bottomMargin = -40;

        // Read the input text file and generate each frame of the GIF
        BufferedReader reader = new BufferedReader(new FileReader(inputFileName));
        String line;
        int frameIndex = 0;
        while ((line = reader.readLine()) != null) {
            // Create a new image for this frame
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();

            // Enable anti-aliasing for smooth font edges
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Fill the background with the specified color
            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, width, height);

            // Draw the text onto the image
            graphics.setColor(fontColor);
            graphics.setFont(font);
            FontMetrics fontMetrics = graphics.getFontMetrics();
            int textWidth = fontMetrics.stringWidth(line);
            int textHeight = fontMetrics.getHeight();
            int x = (width - textWidth) / 2;
            int y = (height - bottomMargin - textHeight) / 2 + fontMetrics.getAscent();
            graphics.drawString(line, x, y);

            // Write the frame to the GIF animation
            writer.writeToSequence(image);

            // Increment the frame index
            frameIndex++;
            graphics.dispose();
        }

        // Clean up resources
        reader.close();
        writer.close();
        output.close();

        // Display a message to the user indicating the output file name and location
        System.out.println("GIF file created: " + outputFile.getAbsolutePath());
    }

}
