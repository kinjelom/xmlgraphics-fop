package org.apache.fop.apps;

import java.io.OutputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.Enumeration;

import org.xml.sax.SAXException;

import org.apache.fop.layout.FontInfo;
import org.apache.fop.layout.Page;
import org.apache.fop.render.Renderer;
import org.apache.fop.layout.AreaTree;
import org.apache.fop.datatypes.IDReferences;
import org.apache.fop.extensions.ExtensionObj;
import org.apache.fop.fo.pagination.PageSequence;
import org.apache.fop.messaging.MessageHandler;

/**
  This class acts as a bridge between the XML:FO parser
  and the formatting/rendering classes. It will queue
  PageSequences up until all the IDs required by them
  are satisfied, at which time it will render the
  pages.<P>

  StreamRenderer is created by Driver and called from
  FOTreeBuilder when a PageSequence is created,
  and AreaTree when a Page is formatted.<P>
*/
public class StreamRenderer extends Object
{
  private static final boolean MEM_PROFILE_WITH_GC = false;

  /**
    Somewhere to get our stats from.
  */
  private Runtime runtime = Runtime.getRuntime();

  /**
    Keep track of the number of pages rendered.
  */
  int pageCount = 0;

  /**
    Keep track of heap memory allocated,
    for statistical purposes.
  */
  private long initialMemory;

  /**
    Keep track of time used by renderer.
  */
  private long startTime;

  /**
    The stream to which this rendering is to be
    written to. <B>Note</B> that some renderers
    do not render to a stream, and that this
    member can therefore be null.
  */
  private OutputStream outputStream;

  /**
    The renderer being used.
  */
  private Renderer renderer;

  /**
    The FontInfo for this renderer.
  */
  private FontInfo fontInfo = new FontInfo();

  /**
    The list of pages waiting to be renderered.
  */
  private Vector renderQueue = new Vector();

  /**
    The current set of IDReferences, passed to the
    areatrees and pages. This is used by the AreaTree
    as a single map of all IDs.
  */
  private IDReferences idReferences = new IDReferences();

  public StreamRenderer(OutputStream outputStream, Renderer renderer)
  {
    this.outputStream = outputStream;
    this.renderer = renderer;
  }

  public IDReferences getIDReferences()
  {
    return idReferences;
  }

  public void startRenderer()
    throws SAXException
  {
    pageCount = 0;

    if (MEM_PROFILE_WITH_GC)
      System.gc();		// This takes time but gives better results

    initialMemory = runtime.totalMemory() - runtime.freeMemory();
    startTime = System.currentTimeMillis();

    try {
      renderer.setupFontInfo(fontInfo);
      renderer.startRenderer(outputStream);
    }
    catch (IOException e)
    {
      throw new SAXException(e);
    }
  }

  public void stopRenderer()
    throws SAXException
  {
    /*
      Force the processing of any more queue elements,
      even if they are not resolved.
    */
    try {
      processQueue(true);
      renderer.stopRenderer(outputStream);
    }
    catch (FOPException e)
    {
      throw new SAXException(e);
    }
    catch (IOException e)
    {
      throw new SAXException(e);
    }

    if (MEM_PROFILE_WITH_GC)
      System.gc();		// This takes time but gives better results

    long memoryNow = runtime.totalMemory() - runtime.freeMemory();
    long memoryUsed = (memoryNow - initialMemory) / 1024L;

    MessageHandler.logln("Initial heap size: " + (initialMemory/1024L) + "Kb");
    MessageHandler.logln("Current heap size: " + (memoryNow/1024L) + "Kb");
    MessageHandler.logln("Total memory used: " + memoryUsed + "Kb");

    if (!MEM_PROFILE_WITH_GC)
    {
      MessageHandler.logln("  Memory use is indicative; no GC was performed");
      MessageHandler.logln("  These figures should not be used comparatively");
    }

    long timeUsed = System.currentTimeMillis() - startTime;

    MessageHandler.logln("Total time used: " + timeUsed + "ms");
    MessageHandler.logln("Pages rendererd: " + pageCount);
    MessageHandler.logln("Avg render time: " + (timeUsed / pageCount) + "ms/page");
  }

  /**
    Format the PageSequence. The PageSequence
    formats Pages and adds them to the AreaTree,
    which subsequently calls the StreamRenderer
    instance (this) again to render the page.
    At this time the page might be printed
    or it might be queued. A page might not
    be renderable immediately if the IDReferences
    are not all valid. In this case we defer
    the rendering until they are all valid.
  */
  public void render(PageSequence pageSequence)
    throws SAXException
  {
    AreaTree a = new AreaTree(this);
    a.setFontInfo(fontInfo);

    try {
      pageSequence.format(a);
    }
    catch (FOPException e)
    {
      throw new SAXException(e);
    }
  }

  public synchronized void queuePage(Page page)
    throws FOPException, IOException
  {
    /*
      Try to optimise on the common case that there are
      no pages pending and that all ID references are
      valid on the current pages. This short-cuts the
      pipeline and renders the area immediately.
    */
    if ((renderQueue.size() == 0) && idReferences.isEveryIdValid())
      renderer.render(page, outputStream);
    else
      addToRenderQueue(page);
    
    pageCount++;
  }

  private synchronized void addToRenderQueue(Page page)
    throws FOPException, IOException
  {
    RenderQueueEntry entry = new RenderQueueEntry(page);
    renderQueue.addElement(entry);

    /*
      The just-added entry could (possibly) resolve the
      waiting entries, so we try to process the queue
      now to see.
    */
    processQueue(false);
  }

  /**
    Try to process the queue from the first entry forward.
    If an entry can't be processed, then the queue can't
    move forward, so return.
  */
  private synchronized void processQueue(boolean force)
    throws FOPException, IOException
  {
    while (renderQueue.size() > 0)
    {
      RenderQueueEntry entry = (RenderQueueEntry) renderQueue.elementAt(0);
      if ((!force) && (!entry.isResolved()))
        break;
      
      renderer.render(entry.getPage(), outputStream);
      
      /* TODO
	Enumeration rootEnumeration =
	  entry.getAreaTree().getExtensions().elements();
	while (rootEnumeration.hasMoreElements())
	  renderTree.addExtension((ExtensionObj) rootEnumeration.nextElement());
      */

      renderQueue.removeElementAt(0);
    }
  }

  /**
    A RenderQueueEntry consists of the Page to be queued,
    plus a list of outstanding ID references that need to be
    resolved before the Page can be renderered.<P>
  */
  class RenderQueueEntry extends Object
  {
    /*
      The Page that has outstanding ID references.
    */
    private Page page;

    /*
      A list of ID references (names).
    */
    private Vector unresolvedIdReferences = new Vector();

    public RenderQueueEntry(Page page)
    {
      this.page = page;

      Enumeration e = idReferences.getInvalidElements();
      while (e.hasMoreElements())
	unresolvedIdReferences.addElement(e.nextElement());
    }

    public Page getPage()
    {
      return page;
    }

    /**
      See if the outstanding references are resolved
      in the current copy of IDReferences.
    */
    public boolean isResolved()
    {
      if ((unresolvedIdReferences.size() == 0) || idReferences.isEveryIdValid())
	return true;

      //
      // See if any of the unresolved references are still unresolved.
      //
      Enumeration e = unresolvedIdReferences.elements();
      while (e.hasMoreElements())
	if (!idReferences.doesIDExist((String) e.nextElement()))
	  return false;

      unresolvedIdReferences.removeAllElements();
      return true;
    }
  }
}
