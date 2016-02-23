package info.fulloo.trygve.graphics;

import info.fulloo.trygve.code_generation.InterpretiveCodeGenerator;
import info.fulloo.trygve.declarations.FormalParameterList;
import info.fulloo.trygve.declarations.Type;
import info.fulloo.trygve.declarations.Type.ClassType;
import info.fulloo.trygve.declarations.TypeDeclaration;
import info.fulloo.trygve.declarations.Declaration.ObjectDeclaration;
import info.fulloo.trygve.expressions.Expression.UnaryopExpressionWithSideEffect.PreOrPost;
import info.fulloo.trygve.run_time.RTClass;
import info.fulloo.trygve.run_time.RTCode;
import info.fulloo.trygve.run_time.RTColorObject;
import info.fulloo.trygve.run_time.RTDynamicScope;
import info.fulloo.trygve.run_time.RTEventObject;
import info.fulloo.trygve.run_time.RunTimeEnvironment;
import info.fulloo.trygve.run_time.RTExpression.RTMessage.RTPostReturnProcessing;
import info.fulloo.trygve.run_time.RTMethod;
import info.fulloo.trygve.run_time.RTObject;
import info.fulloo.trygve.run_time.RTObjectCommon;
import info.fulloo.trygve.run_time.RTObjectCommon.RTContextObject;
import info.fulloo.trygve.run_time.RTObjectCommon.RTIntegerObject;
import info.fulloo.trygve.run_time.RTObjectCommon.RTStringObject;
import info.fulloo.trygve.run_time.RTType;
import info.fulloo.trygve.semantic_analysis.StaticScope;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.util.Map;
import java.util.Vector;

// From DrawText.java

public class GraphicsPanel extends Panel implements ActionListener, RTObject {
	public GraphicsPanel(final RTObjectCommon rTPanel) {
		rTPanel_ = rTPanel;
		
		rectangles_ = new Vector<Rectangle>();
		rectColors_ = new Vector<Color>();
		
		// A line is a funny kind of Rectangle (Javathink)
		lines_ = new Vector<Rectangle>();
		lineColors_ = new Vector<Color>();
		
		ellipses_ = new Vector<Ellipse2D>();
		ellipseColors_ = new Vector<Color>();
		
		strings_ = new Vector<StringRecord>();
	}
	
	public void setBackground(final RTObject colorArg) {
		assert colorArg instanceof RTColorObject;
		final Color color = ((RTColorObject)colorArg).color();
		this.setBackground(color);
	}
	public void setForeground(final RTObject colorArg) {
		assert colorArg instanceof RTColorObject;
		final Color color = ((RTColorObject)colorArg).color();
		this.setBackground(color);
	}
	public void drawLine(final RTObject fromXArg, final RTObject fromYArg, final RTObject toXArg, final RTObject toYArg) {
		assert fromXArg instanceof RTIntegerObject;
		assert fromYArg instanceof RTIntegerObject;
		assert toXArg instanceof RTIntegerObject;
		assert toYArg instanceof RTIntegerObject;
		final int fromX = (int)((RTIntegerObject)fromXArg).intValue();
		final int fromY = (int)((RTIntegerObject)fromYArg).intValue();
		final int toX = (int)((RTIntegerObject)toXArg).intValue();
		final int toY = (int)((RTIntegerObject)toYArg).intValue();
		final Rectangle newRect = new Rectangle(fromX, fromY, Math.abs(toX-fromX), Math.abs(toY-fromY));
		this.addLine(newRect, null);
	}
	public void drawRect(final RTObject fromXArg, final RTObject fromYArg, final RTObject heightArg, final RTObject widthArg) {
		assert fromXArg instanceof RTIntegerObject;
		assert fromYArg instanceof RTIntegerObject;
		assert widthArg instanceof RTIntegerObject;
		assert heightArg instanceof RTIntegerObject;
		final int fromX = (int)((RTIntegerObject)fromXArg).intValue();
		final int fromY = (int)((RTIntegerObject)fromYArg).intValue();
		final int width = (int)((RTIntegerObject)widthArg).intValue();
		final int height = (int)((RTIntegerObject)heightArg).intValue();
		final Rectangle newRect = new Rectangle(fromX, fromY, width, height);
		this.addRectangle(newRect, null);
	}
	public void drawEllipse(final RTObject xArg, final RTObject yArg, final RTObject widthArg, final RTObject heightArg) {
		assert xArg instanceof RTIntegerObject;
		assert yArg instanceof RTIntegerObject;
		assert widthArg instanceof RTIntegerObject;
		assert heightArg instanceof RTIntegerObject;
		final int x = (int)((RTIntegerObject)xArg).intValue();
		final int y = (int)((RTIntegerObject)yArg).intValue();
		final int width = (int)((RTIntegerObject)widthArg).intValue();
		final int height = (int)((RTIntegerObject)heightArg).intValue();
		this.addEllipse(x, y, width, height, null);
	}
	public void drawString(final RTObject xArg, final RTObject yArg, final RTObject stringArg) {
		assert xArg instanceof RTIntegerObject;
		assert yArg instanceof RTIntegerObject;
		assert stringArg instanceof RTStringObject;
		final int x = (int)((RTIntegerObject)xArg).intValue();
		final int y = (int)((RTIntegerObject)yArg).intValue();
		final String string = ((RTStringObject)stringArg).stringValue();
		this.addString(x, y, string, null);
	}
	
	public void handleEventProgrammatically(final Event e) {
		final RTType rTType = rTPanel_.rTType();
		assert rTType instanceof RTClass;
		final TypeDeclaration typeDeclaration = ((RTClass)rTType).typeDeclaration();
		final Type eventType = StaticScope.globalScope().lookupTypeDeclaration("Event");
		final FormalParameterList pl = new FormalParameterList();
		final ObjectDeclaration self = new ObjectDeclaration("this", typeDeclaration.type(), typeDeclaration.lineNumber());
		final ObjectDeclaration event = new ObjectDeclaration("e", eventType, 0);

		pl.addFormalParameter(event);
		pl.addFormalParameter(self);
		
		final RTMethod hE = rTType.lookupMethod("handleEvent", pl);
		if (null != hE) {
			this.dispatchInterrupt(hE, e);
		}
	}
	
	private void dispatchInterrupt(final RTMethod method, final Event e) {
		// Just do a procedure call
		final RTCode halt = null;
		final ClassType eventType = (ClassType)StaticScope.globalScope().lookupTypeDeclaration("Event");
		final RTType rTType = InterpretiveCodeGenerator.scopeToRTTypeDeclaration(eventType.enclosedScope());
		final RTPostReturnProcessing retInst = new RTPostReturnProcessing(halt, "Interrupt");
		final RTEventObject event = new RTEventObject(e, rTType);
		
		RunTimeEnvironment.runTimeEnvironment_.pushStack(event);
		RunTimeEnvironment.runTimeEnvironment_.pushStack(retInst);
		RunTimeEnvironment.runTimeEnvironment_.setFramePointer();
		
		final RTDynamicScope activationRecord = new RTDynamicScope(
				method.name(),
				RunTimeEnvironment.runTimeEnvironment_.currentDynamicScope());
		activationRecord.addObjectDeclaration("event", rTType);
		activationRecord.addObjectDeclaration("this", null);
		activationRecord.setObject("event", event);
		activationRecord.setObject("this", rTPanel_);
		RunTimeEnvironment.runTimeEnvironment_.pushDynamicScope(activationRecord);
		
		RTCode pc = method;
		do {
			pc = RunTimeEnvironment.runTimeEnvironment_.runner(pc);
		} while (null != pc);
	}
	
	// ------------------ Internal stuff ------------------------------
	
	private static class StringRecord {
		public StringRecord(final int x, final int y, final String string, final Color color) {
			x_ = x;
			y_ = y;
			string_ = string;
			color_ = color;
			if (null == color_) {
				color_ = Color.black;
			}
		}
		
		public String toString() {
			return string_;
		}
		
		public int x() { return x_; }
		public int y() { return y_; }
		public Color color() { return color_; }
		
		private final int x_, y_;
		private String string_;
		private Color color_;
	}
	
	@Override public void actionPerformed(final ActionEvent event) {
		assert false;
	}
	
	@Override public boolean handleEvent(final Event e) {
		switch (e.id) {
		  case Event.MOUSE_DOWN:
		    return true;
		  case Event.MOUSE_UP:
			this.handleEventProgrammatically(e);
		    repaint();
		    return true;
		  case Event.MOUSE_MOVE:
			return true;
		  case Event.MOUSE_DRAG:
		    repaint();
		    return true;
		  case Event.WINDOW_DESTROY:
		    System.exit(0);
		    return true;
		  case Event.MOUSE_EXIT:
		  	return true;
		  case Event.MOUSE_ENTER:
			return true;
		  default:
		    return false;
		}
	}
	
	@Override public void paint(final Graphics g) {
		/* draw the current rectangles */
		g.setColor(getForeground());
		g.setPaintMode();
		for (int i=0; i < rectangles_.size(); i++) {
		    final Rectangle p = rectangles_.elementAt(i);
		    g.setColor((Color)rectColors_.elementAt(i));
		    if (p.width != -1) {
		    	g.drawLine(p.x, p.y, p.width, p.height);
		    } else {
		    	g.drawLine(p.x, p.y, p.x, p.y);
		    }
		}
		
		/* draw the current lines */
		g.setColor(getForeground());
		g.setPaintMode();
		for (int i=0; i < lines_.size(); i++) {
		    final Rectangle p = lines_.elementAt(i);
		    g.setColor((Color)lineColors_.elementAt(i));
		    g.drawLine(p.x, p.y, p.x+p.width, p.y+p.height);
		}
		
		/* draw the current ellipses */
		g.setColor(getForeground());
		g.setPaintMode();
		for (int i=0; i < ellipses_.size(); i++) {
		    final Ellipse2D p = ellipses_.elementAt(i);
		    g.setColor((Color)ellipseColors_.elementAt(i));
		    g.drawOval((int)p.getCenterX(), (int)p.getCenterY(), (int)p.getWidth(), (int)p.getHeight());
		}
		
		/* draw the current texts */
		g.setColor(getForeground());
		g.setPaintMode();
		for (int i=0; i < strings_.size(); i++) {
		    final StringRecord p = strings_.elementAt(i);
		    g.setColor((Color)strings_.elementAt(i).color());
		    g.drawString(p.toString(), p.x(), p.y());
		}
	}
	
	public void addRectangle(final Rectangle rect, final Color color) {
		rectangles_.addElement(rect);
		rectColors_.addElement(color);
	}
	
	public void addLine(final Rectangle line, final Color color) {
		lines_.addElement(line);
		lineColors_.addElement(color);
	}
	
	public void addEllipse(int x, int y, int width, int height, final Color color) {
		final Ellipse2D ellipse = new Ellipse2D.Float(x, y, width, height);
		ellipses_.addElement(ellipse);
		ellipseColors_.addElement(color);
	}
	
	public void addString(int x, int y, final String string, final Color color) {
		final StringRecord stringRecord = new StringRecord(x, y, string, color);
		strings_.addElement(stringRecord);
	}
	
	private final Vector<Rectangle> rectangles_;
	private final Vector<Color> rectColors_;
	
	private final Vector<Rectangle> lines_;
	private final Vector<Color> lineColors_;
	
	private final Vector<Ellipse2D> ellipses_;
	private final Vector<Color> ellipseColors_;
	
	private final Vector<StringRecord> strings_;
	private RTObjectCommon rTPanel_;
	
	private static final long serialVersionUID = 238269472;
	private int referenceCount_ = 1;

	// Junk so that we fit into the RTObject framework
	@Override public RTObject getObject(String name) { assert false; return null; }
	@Override public void addObjectDeclaration(String objectName, RTType type) { assert false; }
	@Override public Map<String, RTType> objectDeclarations() { assert false; return null; }
	@Override public void setObject(String objectName, RTObject object) { assert false; }
	@Override public RTType rTType() { assert false; return null; }
	@Override public boolean isEqualTo(Object another) { assert false; return false; }
	@Override public boolean gt(RTObject another) { assert false; return false; }
	@Override public int compareTo(Object other) { assert false; return 0; }
	@Override public boolean equals(RTObject other) { assert false; return false; }
	@Override public RTObject plus(RTObject other) { assert false; return null; }
	@Override public RTObject minus(RTObject other) { assert false; return null; }
	@Override public RTObject times(RTObject other) { assert false; return null; }
	@Override public RTObject divideBy(RTObject other) { assert false; return null; }
	@Override public RTObject modulus(RTObject other) { assert false; return null; }
	@Override public RTObject unaryPlus() { assert false; return null; }
	@Override public RTObject unaryMinus() { assert false; return null; }
	@Override public RTObject logicalOr(RTObject other) { assert false; return null; }
	@Override public RTObject logicalAnd(RTObject other) { assert false; return null; }
	@Override public RTObject logicalXor(RTObject other) { assert false; return null; }
	@Override public RTObject unaryLogicalNegation() { assert false; return null; }
	@Override public RTObject preIncrement() { assert false; return null; }
	@Override public RTObject postIncrement() { assert false; return null; }
	@Override public RTObject preDecrement() { assert false; return null; }
	@Override public RTObject postDecrement() { assert false; return null; }
	@Override public RTObject performUnaryOpOnObjectNamed(String idName, String operator,
			PreOrPost preOrPost_) { assert false; return null; }
	@Override public RTObject toThePowerOf(RTObject exponent) { assert false; return null; }
	@Override public RTObject dup() { assert false; return null; }
	@Override public void incrementReferenceCount() { referenceCount_++; }
	@Override public void decrementReferenceCount() { --referenceCount_; }
	@Override public long referenceCount() { return referenceCount_; }
	@Override public void enlistAsRolePlayerForContext(String roleName,
			RTContextObject contextInstance) { assert false; }
	@Override public void unenlistAsRolePlayerForContext(String roleName,
			RTContextObject contextInstance) { assert false; }
	@Override public void enlistAsStagePropPlayerForContext(String stagePropName,
			RTContextObject contextInstance) { assert false; }
	@Override public void unenlistAsStagePropPlayerForContext(String stagePropName,
			RTContextObject contextInstance) { assert false; }
	@Override public String getText() { assert false; return null; }
}
