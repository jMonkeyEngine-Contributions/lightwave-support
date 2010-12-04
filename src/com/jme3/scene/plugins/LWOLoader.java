/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.jme3.scene.plugins;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.export.binary.BinaryExporter;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.shader.VarType;
import com.jme3.util.BufferUtils;

public class LWOLoader implements AssetLoader {
    private static Logger logger = Logger.getLogger("LWOLoader");

    /**
     * Creates a node from a .lwo InputStream and then writes that node to the given
     * OutputStream in jME's binary format
     * @param LWOStream An InputStream pointing to the .lwo file
     * @param o The stream to write it's binary equivalent to
     * @throws java.io.IOException If anything funky goes wrong with reading information
     */
    public void convert(InputStream LWOStream,OutputStream o, AssetManager assetManager) throws IOException {
        if (LWOStream==null)
            throw new NullPointerException("Unable to load null streams");
        Node newnode=new Node("lwo model");
        new LWOLoader.LWOLoaderCopy(LWOStream,newnode, assetManager);
        BinaryExporter.getInstance().save(newnode,o);
    }

    private static class LWOLoaderCopy {
        private static final long serialVersionUID = 1L;
        private Node mynode;
        private DataInputStream reader;
        private char[] idTag = new char[4];
        private Vector3f[] points = null;
        private String[] surfaces = null;
        private List<Surface> surfaceList = new ArrayList<Surface>();
        private Map<Integer,List<int[]>> polyMap;
        private AssetManager assetManager;

        public LWOLoaderCopy(InputStream LWOStream,Node node, AssetManager assetManager) throws IOException {
            this.mynode=node;
            this.assetManager = assetManager;
            load(LWOStream);
        }

        public void load(InputStream is) throws IOException {
            if (null == is) {
                logger.log(Level.SEVERE,"Null URL could not load LWO.");
                return;
            }

            try {
                is.available();

                reader = new DataInputStream(is);
                readIdTag(reader,idTag);

                if (!String.valueOf(idTag).equals("FORM")) {
                    throw new IOException("Unable to read IFF file header, found '"+String.valueOf(idTag)+"'");
                }

                int size = reader.readInt();
                byte[] form = new byte[size];

                reader.readFully(form);
                reader.close();

                reader = new DataInputStream(new ByteArrayInputStream(form));
                readIdTag(reader,idTag);

                if (!String.valueOf(idTag).equals("LWOB")) {
                    throw new IOException("File is not LWOB object");
                }

                byte[] chunk;
                String chunkName;
                while(reader.available() > 0) {
                    readIdTag(reader,idTag);

                    chunkName = String.valueOf(idTag);

                    size = reader.readInt();
                    chunk = new byte[size];
                    reader.readFully(chunk);
                    if (chunkName.equals("PNTS")) {
                        points = readPoints(chunk);
                    }
                    else if (chunkName.equals("SRFS")) {
                        surfaces = readSurfaces(chunk);
                    }
                    else if (chunkName.equals("SURF")) {
                        Surface newSurf = readSurf(chunk);
                        if (newSurf != null)
                            surfaceList.add(newSurf);
                    }
                    else if (chunkName.equals("POLS")) {
                        polyMap = readPolys(chunk);
                    }
                    else {
                        System.out.println("  chunk: "+chunkName+ " bytes: "+size);
                    }
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE,"Could not load " + is.toString(),e);
                e.printStackTrace();
            }

            if (points == null || points.length==0) {
                throw new IOException("No points found");
            }

            if (surfaces == null || surfaces.length==0) {
                throw new IOException("No surfaces found");
            }

            if (surfaceList.isEmpty()) {
                throw new IOException("No surface descriptions found");
            }

            if (polyMap.isEmpty()) {
                throw new IOException("No polys found");
            }

            Map<String,Surface> surfaceMap = new HashMap<String,Surface>();
            for (Surface surface : surfaceList) {
                surfaceMap.put(surface.surfaceName,surface);
            }

            FloatBuffer vertBuffer = BufferUtils.createFloatBuffer(points);
            for (int i = 0 ; i < surfaces.length; i++) {
                Mesh mesh = new Mesh();
                Geometry geometry = new Geometry(surfaces[i],mesh);

                int triangleCount = 0;
                List<int[]> polys = polyMap.get(i+1);
                for (int[] poly : polys) triangleCount+= poly.length-2;
                IntBuffer indexBuffer = BufferUtils.createIntBuffer(triangleCount*3);

                mesh.setBuffer(Type.Position, 3, vertBuffer);
                mesh.setBuffer(Type.Index, 3, indexBuffer);

                for (int[] poly : polys) {
                    for (int j=2;j<poly.length;j++) {
                        indexBuffer.put(poly[0]).put(poly[j-1]).put(poly[j]);
                    }
                }

                Surface surface = surfaceMap.get(surfaces[i]);
                if (surface.diffuse != null || surface.specular != null || surface.color != null) {
                    Material ms = new Material(assetManager,"Common/MatDefs/Light/Lighting.j3md");
                    geometry.setMaterial(ms);

                    if (surface.diffuse != null) {
                        ms.setColor("m_Diffuse", surface.diffuse);
                    }
                    if (surface.specular != null) {
                        ms.setColor("m_Specular", surface.specular);
                    }
                    if (surface.color != null) {
                        ms.setColor("m_Ambient", surface.color);
                    }
                    if (surface.glossy>0.0f) {
                        ms.setParam("m_Shininess", VarType.Float,surface.glossy*128);
                    }
                    ms.setBoolean("m_UseMaterialColors", true);
                }
                mynode.attachChild(geometry);
            }
        }

        private static void readIdTag(DataInputStream dis, char[] idTag) throws IOException {
            idTag[0] = (char)dis.readByte();
            idTag[1] = (char)dis.readByte();
            idTag[2] = (char)dis.readByte();
            idTag[3] = (char)dis.readByte();
        }

        private static Vector3f[] readPoints(byte[] chunk) throws IOException {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(chunk));
            int numPoints = chunk.length/12;
            Vector3f[] points = new Vector3f[numPoints];
            for (int i = 0; i<numPoints; i++) {
                points[i] = new Vector3f(dis.readFloat(),dis.readFloat(),dis.readFloat());
            }
            dis.close();
            return points;
        }

        private static String[] readSurfaces(byte[] chunk) throws IOException {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(chunk));
            LinkedList<String> surfaces = new LinkedList<String>();
            String surfaceName;
            while (dis.available()>0) {
                surfaceName = readString(dis);
                surfaces.addLast(surfaceName);
            }
            dis.close();
            return surfaces.toArray(new String[0]);
        }

        private static String readString(DataInputStream dis) throws IOException {
            int index = 0;
            char[] string = new char[256];
            string[index] = (char)dis.readByte();
            while (string[index] != 0) {
                index++;
                string[index] = (char)dis.readByte();
            }

            dis.mark(2);
            if (dis.available()>0 && dis.readByte() != 0) dis.reset();

            return String.valueOf(string).substring(0, index);
        }

        private static Map<Integer,List<int[]>> readPolys(byte[] chunk) throws IOException {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(chunk));
            Map<Integer,List<int[]>> polyMap = new HashMap<Integer,List<int[]>>();

            int size;
            int surface;
            List<int[]> list = null;
            while (dis.available()>0) {
                size = dis.readUnsignedShort();
                int[] poly = new int[size];
                for (int i=0;i<size;i++) {
                    poly[i] = dis.readUnsignedShort();
                }
                surface = dis.readUnsignedShort();

                list = polyMap.get(surface);
                if (list == null) {
                    list = new ArrayList<int[]>();
                    polyMap.put(surface, list);
                }
                list.add(poly);
            }
            dis.close();

            return polyMap;
        }

        private static Surface readSurf(byte[] chunk) throws IOException {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(chunk));
            Map<Integer,List<int[]>> polyMap = new HashMap<Integer,List<int[]>>();

            int size;
            char[] idTag = new char[4];
            String chunkName;
            String surfName = readString(dis);

            Surface surface = new Surface();
            surface.surfaceName = surfName;

            while (dis.available()>0) {
                readIdTag(dis,idTag);

                chunkName = String.valueOf(idTag);

                size = dis.readUnsignedShort();
                if (chunkName.equals("COLR")) {
                    surface.color = new ColorRGBA(1,1,1,1);
                    surface.color.r = ((float)dis.readByte())/255f;
                    surface.color.g = ((float)dis.readByte())/255f;
                    surface.color.b = ((float)dis.readByte())/255f;
                    dis.readByte();
                }
                else if (chunkName.equals("FLAG")) {
                    surface.flags = dis.readShort();
                }
                else if (chunkName.equals("DIFF")) {
                    surface.diffuse = surface.color.clone();
                    surface.diffuse.a = ((float)dis.readUnsignedShort())/256f;
                }
                else if (chunkName.equals("SPEC")) {
                    surface.specular = surface.color.clone();
                    surface.specular.a = ((float)dis.readUnsignedShort())/256f;
                }
                else if (chunkName.equals("GLOS")) {
                    surface.glossy = ((float)dis.readUnsignedShort())/256f;
                }
                else if (chunkName.equals("TRAN")) {
                    surface.alpha = ((float)dis.readUnsignedShort())/256f;
                }
                else if (chunkName.equals("VDIF")) {
                    surface.diffuse = surface.color.clone();
                    surface.diffuse.a = dis.readFloat();
                }
                else if (chunkName.equals("VTRN")) {
                    surface.alpha=dis.readFloat();
                }
                else if (chunkName.equals("VSPC")) {
                    surface.specular = surface.color.clone();
                    surface.specular.a = dis.readFloat();
                }
                else if (chunkName.equals("TFLG")) {
                    surface.textureFlags = dis.readUnsignedShort();
                }
                else if (chunkName.equals("REFL")) {
                    surface.reflective = ((float)dis.readUnsignedShort())/256f;
                }
                else if (chunkName.equals("TAAS")) {
                    surface.textureAntiAliasing = dis.readFloat();
                }
                else if (chunkName.equals("TSIZ")) {
                    surface.textureSize = new Vector3f();
                    surface.textureSize.x = dis.readFloat();
                    surface.textureSize.y = dis.readFloat();
                    surface.textureSize.z = dis.readFloat();
                }
                else if (chunkName.equals("TIMG")) {
                    surface.textureImage = readString(dis);
                }
                else if (chunkName.equals("TALP")) {
                    surface.textureAlphaImage = readString(dis);
                }
                else if (chunkName.equals("TCTR")) {
                    surface.textureCenter = new Vector3f();
                    surface.textureCenter.x = dis.readFloat();
                    surface.textureCenter.y = dis.readFloat();
                    surface.textureCenter.z = dis.readFloat();
                }
                else if (chunkName.equals("TWRP")) {
                    surface.textureWrapS = dis.readUnsignedShort();
                    surface.textureWrapT = dis.readUnsignedShort();
                }
                else if (chunkName.equals("BTEX")) {
                    String tex = readString(dis);
                    surface.BTEX=tex;
                }
                else if (chunkName.equals("CTEX")) {
                    String tex = readString(dis);
                    surface.CTEX=tex;
                }
                else if (chunkName.equals("DTEX")) {
                    String tex = readString(dis);
                    surface.DTEX=tex;
                }
                else if (chunkName.equals("LTEX")) {
                    String tex = readString(dis);
                    surface.LTEX=tex;
                }
                else if (chunkName.equals("RTEX")) {
                    String tex = readString(dis);
                    surface.RTEX=tex;
                }
                else if (chunkName.equals("STEX")) {
                    String tex = readString(dis);
                    surface.STEX=tex;
                }
                else if (chunkName.equals("TTEX")) {
                    String tex = readString(dis);
                    surface.TTEX=tex;
                }
                else if (chunkName.equals("SMAN")) { //smoothing angle
                    float val = dis.readFloat();
                }
                else if (chunkName.equals("RIND")) {
                    float val = dis.readFloat();
                }
                else {
                    byte[] subChunk = new byte[size];
                    dis.readFully(subChunk);
                    subChunk = null;
                }
            }
            dis.close();
            return surface;
        }
    }
/*
    434f4c52 0004                COLR {            base color is yellow
    f0 b4 00 00                    240, 180, 0
    464c4147 0002                FLAG {            surface is double-sided
    0100                           [00100000000]
    44494646 0002                DIFF {            fixed 60% diffuse
    009a                           154
    53504543 0002                SPEC {            fixed 80% specular
    00cd                           205
    474c4f53 0002                GLOS {            "High" glossiness
    0100                           256
    5245464c 0002                REFL {            fixed 20% reflective
    0033                           51
    5452414e 0002                TRAN {            fixed 40% transparent
    0066                           102
    54464c47 0002                TFLG {            Y-axis; world-coords;
    006a                           [1101010]       pixel blending; antialiasing
    54414153 0004                TAAS {            texture antializing strength
    3f800000                       1.0             100%
    54414d50 0004                TAMP {            bump amplitude
    3f000000                       1.5             150%
    54495030 0002                TIP0 {            first integer parameter
    0003                           3               3 fractal noise frequences

   CTEX, DTEX, STEX, RTEX, TTEX, LTEX, BTEX (color, diffuse, spec, reflective, texture, luminosity, bump texture)
 */
    private static class Surface {
        String surfaceName;
        int flags;
        ColorRGBA diffuse = null;
        ColorRGBA specular = null;
        ColorRGBA color = null;
        float reflective;
        float glossy;
        float alpha;
        String textureImage = null;
        String textureAlphaImage = null;
        int textureFlags;
        float textureAntiAliasing;
        int textureWrapS, textureWrapT;
        Vector3f textureCenter = null;
        Vector3f textureSize = null;
        String CTEX, DTEX, STEX, RTEX, TTEX, LTEX, BTEX;

        public String toString() {
            return
              "    Name:       "+surfaceName+
            "n    Flags:      "+Integer.toBinaryString(flags)+
            "n    Diffuse:    "+diffuse+
            "n    Specular:   "+specular+
            "n    Color:      "+color+
            "n    Reflective: "+reflective+
            "n    Glossy:     "+glossy+
            "n    Alpha:      "+alpha+
            "n    Texture:    "+textureImage+
            "n    Tex-Alpha:  "+textureAlphaImage+
            "n    Tex-AA:     "+textureAntiAliasing+
            "n    Tex-Flags:  "+Integer.toBinaryString(textureFlags)+
            "n    Tex-WrapS:  "+textureWrapS+
            "n    Tex-WrapT:  "+textureWrapT+
            "n    Tex-Center: "+textureCenter+
            "n    Tex-Size:   "+textureSize+
            "n    CTEX:       "+CTEX+
            "n    DTEX:       "+DTEX+
            "n    STEX:       "+STEX+
            "n    RTEX:       "+RTEX+
            "n    TTEX:       "+TTEX+
            "n    LTEX:       "+LTEX+
            "n    BTEX:       "+BTEX
                 ;

        }
    }
    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        Node newnode=new Node("lwo model");
        new LWOLoader.LWOLoaderCopy(assetInfo.openStream(),newnode,assetInfo.getManager());
        return newnode;
    }
}