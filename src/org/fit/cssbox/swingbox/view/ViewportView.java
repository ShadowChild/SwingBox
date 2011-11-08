package org.fit.cssbox.swingbox.view;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JViewport;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.Element;
import javax.swing.text.View;

import org.fit.cssbox.swingbox.SwingBoxDocument;
import org.fit.cssbox.swingbox.SwingBoxEditorKit;

/**
 * The Class ViewportView.
 *
 * @author Peter Bielik
 * @version 1.0
 * @since 1.0 - 22.2.2011
 */
public class ViewportView extends BlockBoxView implements ComponentListener{
    private Reference<JViewport> cachedViewPort;
    private JEditorPane editor;
    private Dimension tmpDimension;


    /**
     * Instantiates a new viewport view.
     *
     * @param elem
     *            the elem
     */
    public ViewportView(Element elem) {
	super(elem);
	tmpDimension = new Dimension();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean validateLayout(Dimension dim) {
	/*
	 * if a new layout is created, everything is built from scratch and valid..
	 * if did not succeed, then world has not changed and mark it is valid
	 */
	boolean result = checkSize(dim);
	if (!result)
	    super.validateLayout(dim);

	return result; // XXX stale to vracia false !!
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void paint(Graphics graphics, Shape allocation) {


	Graphics2D g;
	if (graphics instanceof Graphics2D) {
	    g = (Graphics2D) graphics;
	} else {
	    throw new RuntimeException("Unknowen graphics enviroment, java.awt.Graphics2D required !");
	}

	Shape oldclip = g.getClip();
	Rectangle absoluteBounds = box.getAbsoluteBounds();
	Rectangle alloc = toRect(allocation);

	//in this place we make an intersection :
	//alloc - should be a "visible" area of component (JEditorPane)
	//g.getClip - the "real" visible area
	//absoluteBounds - our point of interest
	//we are a root, so any child needs only to intersects with given allocation ;-)
	//the result is a rectangle, where content should be rendered


	intersection(alloc, absoluteBounds, tmpRect);
	intersection(toRect(oldclip),	tmpRect, tmpRect);


	box.getVisualContext().updateGraphics(g);
	box.drawBackground(g);

	//we dont set any clipping here, it is rosponsible of a child
	int count = getViewCount();
	for (int i=0; i<count; i++) {
	    getView(i).paint(g, tmpRect);
	}

	g.setClip(oldclip);
    }


    /* (non-Javadoc)
     * @see org.fit.cssbox.swingbox.view.BlockBoxView#isVisible()
     */
    @Override
    public boolean isVisible() {
	return true;
    }


    private void hook() {
	Container container = getContainer();
	Container parentContainer;

	if (container != null
		&& (container instanceof javax.swing.JEditorPane)
		&& (parentContainer = container.getParent()) != null
		&& (parentContainer instanceof javax.swing.JViewport)) {

	    editor = (JEditorPane) container;

	    //our parent is a JScrollPane (JViewPort)
	    JViewport viewPort = (JViewport)parentContainer;
	    Object cachedObject;

	    if (cachedViewPort != null) {
		if ((cachedObject = cachedViewPort.get()) != null) {
		    if (cachedObject != viewPort) {
			//parent is different from previous, remove listener
			((JComponent)cachedObject).removeComponentListener(this);
		    }
		} else {
		    //parent has been garbage-collected
		    cachedViewPort = null;
		}
	    }

	    if (cachedViewPort == null) {
		//hook it
		viewPort.addComponentListener(this);
		cachedViewPort = new WeakReference<JViewport>(viewPort);
	    }

	    //System.err.println("Hooked at : " + viewPort.getExtentSize());
	    //checkSize(viewPort.getExtentSize());

	} else {
	    unhook();
	}
    }

    private void unhook() {
	if (cachedViewPort != null) {
	    Object cachedObject;
	    if ((cachedObject = cachedViewPort.get()) != null) {
		((JComponent)cachedObject).removeComponentListener(this);
	    }
	    cachedViewPort = null;
	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setParent(View parent) {
	//do what we need
	super.setParent(parent);
	if (parent == null) {
	    unhook();
	    editor = null;
	} else {
	    hook();
	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void componentResized(ComponentEvent e) {
	if ( (e.getSource() instanceof JViewport) ) {
	checkSize(((JViewport)e.getSource()).getExtentSize());
	}

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void componentHidden(ComponentEvent e) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void componentMoved(ComponentEvent e) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void componentShown(ComponentEvent e) {
    }


    private boolean checkSize(Dimension extentSize) {
	if (extentSize.width == 0 || extentSize.height == 0) {
	    return false;
	}
	if (!tmpDimension.equals(extentSize) && extentSize.width > box.getMinimalWidth() && extentSize.width < box.getMaximalWidth()) {

	    Document doc = getDocument();
	    tmpDimension.setSize(extentSize);

	    if (doc instanceof SwingBoxDocument) {
		return doLayout((SwingBoxDocument)doc, tmpDimension);
	    }
	}


	return false;
    }

    private boolean doLayout(SwingBoxDocument doc, Dimension dim) {
	try {

	    EditorKit kit = editor.getEditorKit();


	    if (kit instanceof SwingBoxEditorKit) {
		((SwingBoxEditorKit)kit).update(doc, box.getViewport(), dim);
	    }


	    preferenceChanged(null, true, true);
	    return true;
	} catch (IOException e) {
	    e.printStackTrace();
	}

	return false;

    }



}