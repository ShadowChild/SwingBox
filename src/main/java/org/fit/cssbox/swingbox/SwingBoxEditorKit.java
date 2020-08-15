/*
 * (c) Peter Bielik and Radek Burget, 2011-2012
 *
 * SwingBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * SwingBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public License
 * along with SwingBox. If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package org.fit.cssbox.swingbox;

import org.apache.commons.io.input.ReaderInputStream;
import org.fit.cssbox.io.DocumentSource;
import org.fit.cssbox.io.StreamDocumentSource;
import org.fit.cssbox.swingbox.util.*;
import org.fit.cssbox.swingbox.util.GeneralEvent.EventType;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.DefaultStyledDocument.ElementSpec;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This is custom implementation of EditorKit for (X)HTML with use of CSSBox.
 * 
 * @author Peter Bielik
 * @version 1.0
 * @since 1.0 - 28.9.2010
 */
@SuppressWarnings("unused")
public class SwingBoxEditorKit extends StyledEditorKit
{
    private static final long serialVersionUID = -2774578978116020429L;

    private CSSBoxAnalyzer cbanalyzer;
    private ViewFactory vfactory;
    private JEditorPane component;
    private final MouseController mcontroller;

    /**
     * Instantiates a new swing box editor kit.
     */
    public SwingBoxEditorKit()
    {
        super();
        String tmp = System.getProperty(Constants.DEFAULT_ANALYZER_PROPERTY,
                Constants.PROPERTY_NOT_SET);
        if (tmp.equals(Constants.PROPERTY_NOT_SET))
        {
            // sets property for default analyzer, the fully qualified classname
            // which is used to instantiate this class by reflection
            System.setProperty(Constants.DEFAULT_ANALYZER_PROPERTY,
                    "org.fit.cssbox.swingbox.util.DefaultAnalyzer");
        }

        tmp = System.getProperty(
                Constants.DOCUMENT_ASYNCHRONOUS_LOAD_PRIORITY_PROPERTY,
                Constants.PROPERTY_NOT_SET);
        if (tmp.equals(Constants.PROPERTY_NOT_SET))
        {
            // property not set, load synchronously !
            System.setProperty(
                    Constants.DOCUMENT_ASYNCHRONOUS_LOAD_PRIORITY_PROPERTY,
                    "-1");
        }

        mcontroller = new MouseController();
    }

    /**
     * Instantiates a new swing box editor kit with CSSBoxAnalyzer set.
     * 
     * @param cba
     *            the CSSBoxAnalyzer to be set
     */
    public SwingBoxEditorKit(CSSBoxAnalyzer cba)
    {
        this();
        this.cbanalyzer = cba;
    }

    @Override
    public void install(JEditorPane c)
    {
        super.install(c);
        c.addMouseListener(mcontroller);
        c.addMouseMotionListener(mcontroller);
        component = c;
    }

    @Override
    public void deinstall(JEditorPane c)
    {
        super.deinstall(c);
        c.removeMouseListener(mcontroller);
        c.removeMouseMotionListener(mcontroller);
        component = null;
    }

    @Override
    public Document createDefaultDocument()
    {
        SwingBoxDocument doc = new SwingBoxDocument();

        // set asynchronous load priority. If set to -1, load synchronously,
        // otherwise load asynchronously, with given priority :)
        // this value is stored as internal property under
        // AbstractDocument.AsyncLoadPriority key.

        // -1 == synchronously
        int priority = -1;
        String tmp = System.getProperty(
                Constants.DOCUMENT_ASYNCHRONOUS_LOAD_PRIORITY_PROPERTY,
                Constants.PROPERTY_NOT_SET);
        if (!tmp.equals(Constants.PROPERTY_NOT_SET))
        {
            try
            {
                priority = Integer.parseInt(tmp);
            } catch (Exception ignored)
            {
            }
        }

        doc.setAsynchronousLoadPriority(priority);

        return doc;
    }

    @Override
    public ViewFactory getViewFactory()
    {
        if (vfactory == null)
        {
            vfactory = new SwingBoxViewFactory();
        }
        return vfactory;
    }

    @Override
    public String getContentType()
    {
        return "text/html";
    }

    @Override
    public Caret createCaret()
    {
        return null;
    }

    @Override
    public void write(OutputStream out, Document doc, int pos, int len)
            throws IOException, BadLocationException
    {
        // this method closes OutputStream
        if (doc instanceof SwingBoxDocument)
        {
            Writer tmpOut = new BufferedWriter(new OutputStreamWriter(out,
                    Charset.defaultCharset()), 8 * 1024);

            writeImpl(tmpOut, (SwingBoxDocument) doc, pos );

            tmpOut.flush();
            tmpOut.close();
        }
        else
        {
            super.write(out, doc, pos, len);
        }
    }

    @Override
    public void write(Writer out, Document doc, int pos, int len)
            throws IOException, BadLocationException
    {
        // this method closes OutputStream
        if (doc instanceof SwingBoxDocument)
        {
            Writer tmpOut = new BufferedWriter(out, 8 * 1024);

            writeImpl(tmpOut, (SwingBoxDocument) doc, pos );

            tmpOut.flush();
            tmpOut.close();
        }
        else
        {
            super.write(out, doc, pos, len);
        }
    }

    @Override
    public void read(InputStream in, Document doc, int pos) throws IOException,
            BadLocationException
    {

        if (doc instanceof org.fit.cssbox.swingbox.SwingBoxDocument)
        {
            readImpl(in, (org.fit.cssbox.swingbox.SwingBoxDocument) doc, pos);
        }
        else
        {
            super.read(in, doc, pos);
        }
    }

    @Override
    public void read(Reader in, Document doc, int pos) throws IOException,
            BadLocationException
    {

        if (doc instanceof org.fit.cssbox.swingbox.SwingBoxDocument)
        {
            InputStream is = new ReaderInputStream(in, UTF_8);
            readImpl(is, (org.fit.cssbox.swingbox.SwingBoxDocument) doc, pos);
        }
        else
        {
            super.read(in, doc, pos);
        }
    }

    /**
     * Updates layout, using new dimensions.
     * 
     * @param doc
     *            the document
     * @param dim
     *            new dimension
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public void update(SwingBoxDocument doc, Dimension dim)
            throws IOException
    {
        ContentReader rdr = new ContentReader();
        List<ElementSpec> elements = rdr.update(dim, getCSSBoxAnalyzer());
        ElementSpec[] elementsArray = elements.toArray( new ElementSpec[0]);
        doc.create(elementsArray);
    }

    /**
     * Allows to set custom CSSBoxAnalyzer
     * 
     * @param cba
     *            the instance of CSSBoxAnalyzer
     * @see CSSBoxAnalyzer
     */
    public void setCSSBoxAnalyzer(CSSBoxAnalyzer cba)
    {
        this.cbanalyzer = cba;
    }

    /**
     * Gets current instance of {@link CSSBoxAnalyzer}
     * 
     * @return the instance of {@link CSSBoxAnalyzer}
     */
    public CSSBoxAnalyzer getCSSBoxAnalyzer()
    {
        if (cbanalyzer == null)
        {
            cbanalyzer = getDefaultAnalyzer();
        }

        return cbanalyzer;
    }

    @SuppressWarnings("rawtypes")
    protected CSSBoxAnalyzer getDefaultAnalyzer()
    {
        // possible to provide custom implementation
        CSSBoxAnalyzer cba = null;
        String cname = System.getProperty(
            Constants.DEFAULT_ANALYZER_PROPERTY,
            Constants.PROPERTY_NOT_SET);

        if (!Constants.PROPERTY_NOT_SET.equals(cname))
        {
            try
            {
                Class c;
                ClassLoader loader = getClass().getClassLoader();
                if (loader != null)
                {
                    c = loader.loadClass(cname);
                }
                else
                {
                    c = Class.forName(cname);
                }

                @SuppressWarnings("unchecked")
                Object o = c.getDeclaredConstructor().newInstance();
                if (o instanceof CSSBoxAnalyzer)
                {
                    cba = (CSSBoxAnalyzer) o;
                }
            } catch (Exception ignored )
            {
            }
        }

        return cba;
    }

    private void readImpl(InputStream in, SwingBoxDocument doc, int pos)
            throws IOException, BadLocationException
    {
        if (component == null)
            throw new IllegalStateException("Component is null, editor kit is probably deinstalled from a JEditorPane.");
        if (pos > doc.getLength() || pos < 0)
        {
            BadLocationException e = new BadLocationException("Invalid location", pos);
            readError(null, e);
            throw e;
        }

        ContentReader rdr = new ContentReader();
        URL url = (URL) doc.getProperty(Document.StreamDescriptionProperty);
        CSSBoxAnalyzer analyzer = getCSSBoxAnalyzer();

        Container parent = component.getParent();
        Dimension dim;
        if ( parent instanceof JViewport )
        {
            dim = ((JViewport) parent).getExtentSize();
        }
        else
        {
            dim = component.getBounds().getSize();
        }

        if (dim.width <= 10)
        {
            // component might not be initialized, use screen size :)
            Dimension tmp = Toolkit.getDefaultToolkit().getScreenSize();
            dim.setSize(tmp.width / 2.5, tmp.height / 2.5);
        }

        List<ElementSpec> elements;
        try
        {
            String ctype = null;
            Object ct = doc.getProperty("Content-Type");
            if (ct != null)
            {
                if (ct instanceof List)
                    ctype = (String) ((List<?>) ct).get(0);
                else
                    ctype = ct.toString();
            }

            DocumentSource docSource = new StreamDocumentSource(in, url, ctype);
            elements = rdr.read(docSource, analyzer, dim);
            String title = analyzer.getDocumentTitle();
            doc.putProperty(Document.TitleProperty, title);
        } catch (IOException e)
        {
            readError(url, e);
            throw e;
        }

        ElementSpec[] elementsArray = elements.toArray( new ElementSpec[0]);
        doc.create(elementsArray);

        readFinish(url);
    }

    private void readError(URL url, Exception e)
    {
        if (component instanceof BrowserPane)
        {
            ((BrowserPane) component).fireGeneralEvent(new GeneralEvent(this, EventType.page_loading_error, url, e));
        }
    }

    private void readFinish(URL url)
    {
        if (component instanceof BrowserPane)
        {
            ((BrowserPane) component).fireGeneralEvent(new GeneralEvent(this,
                    EventType.page_loading_end, url, null));
        }
    }

    private void writeImpl( Writer out, SwingBoxDocument doc, int pos )
            throws BadLocationException, IOException
    {
        if (pos > doc.getLength() || pos < 0) {
            throw new BadLocationException("Invalid location", pos);
        }

        ContentWriter wrt = new ContentWriter();
        StringBuilder sb = wrt.write(getCSSBoxAnalyzer().getDocument());
        out.write(sb.toString());
        out.flush();
    }

}
