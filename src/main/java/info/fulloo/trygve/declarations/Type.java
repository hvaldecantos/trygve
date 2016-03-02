package info.fulloo.trygve.declarations;

/*
 * Trygve IDE 1.5
 *   Copyright (c)2016 James O. Coplien, jcoplien@gmail.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 *  For further information about the trygve project, please contact
 *  Jim Coplien at jcoplien@gmail.com
 * 
 */

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import info.fulloo.trygve.declarations.Declaration.MethodDeclaration;
import info.fulloo.trygve.declarations.Declaration.MethodSignature;
import info.fulloo.trygve.declarations.Declaration.ObjectDeclaration;
import info.fulloo.trygve.declarations.Declaration.RoleArrayDeclaration;
import info.fulloo.trygve.declarations.Declaration.RoleDeclaration;
import info.fulloo.trygve.declarations.Declaration.StagePropArrayDeclaration;
import info.fulloo.trygve.declarations.Declaration.TemplateDeclaration;
import info.fulloo.trygve.error.ErrorLogger;
import info.fulloo.trygve.error.ErrorLogger.ErrorType;
import info.fulloo.trygve.expressions.ExpressionStackAPI;
import info.fulloo.trygve.parser.Pass1Listener;
import info.fulloo.trygve.run_time.RTCode;
import info.fulloo.trygve.run_time.RTExpression.RTNullExpression;
import info.fulloo.trygve.semantic_analysis.StaticScope;
import info.fulloo.trygve.semantic_analysis.StaticScope.StaticRoleScope; 

public abstract class Type implements ExpressionStackAPI
{
	enum HierarchySelector { ThisClassOnly, AlsoSearchBaseClass };
	public Type(final StaticScope enclosedScope) {
		super();
		enclosedScope_ = enclosedScope;
		staticObjectDeclarationDictionary_ = new LinkedHashMap<String, ObjectDeclaration>();
		staticObjects_ = new LinkedHashMap<String, RTCode>();
		lineNumber_ = null != enclosedScope?
					(null != enclosedScope.associatedDeclaration()?
							enclosedScope.associatedDeclaration().lineNumber(): 0):
					0;
	}
	public StaticScope enclosedScope() {
		return enclosedScope_;
	}
	public void addDeclaration(final Declaration declaration) {
		enclosedScope_.declare(declaration);
	}
	public void declareStaticObject(final ObjectDeclaration declaration) {
		final String objectName = declaration.name();
		if (staticObjectDeclarationDictionary_.containsKey(objectName)) {
			ErrorLogger.error(ErrorType.Internal, declaration.lineNumber(), "Multiple definitions of static member ",
					objectName, "", "");
		} else {
			staticObjectDeclarationDictionary_.put(objectName, declaration);
			staticObjects_.put(objectName, new RTNullExpression());
		}
	}
	public ObjectDeclaration lookupStaticObjectDeclaration(final String objectName) {
		return staticObjectDeclarationDictionary_.get(objectName);
	}
	public Map<String,ObjectDeclaration> staticObjects() {
		return staticObjectDeclarationDictionary_;
	}
	public String name() {
		String retval = "*unknwown*";
		if (enclosedScope_ != null) {
			retval = enclosedScope_.name();
		}
		return retval;
	}
	public boolean isBaseClassOf(final Type aDerived) {
		// false for all but class types
		return false;
	}
	public boolean canBeLhsOfBinaryOperatorForRhsType(final String operator, final Type type) {
		// Type
		boolean retval = false;
		if ((operator.equals("!=") || operator.equals("==")) && type.pathName().equals("Null")) {
			// Can always compare with null
			retval = true;
		} else if (operator.equals("!=") || operator.equals("==") || operator.equals("<") ||
				operator.equals(">") || operator.equals("<=") || operator.equals(">=")) {
			// Valid if compareTo(X) is defined on us. Probably needs some loosening up
			final FormalParameterList parameterList = new FormalParameterList();
			ObjectDeclaration self = new ObjectDeclaration("this", this, 0);
			parameterList.addFormalParameter(self);
			ObjectDeclaration other = new ObjectDeclaration("other", this, 0);
			parameterList.addFormalParameter(other);
			MethodDeclaration compareTo = this.enclosedScope().lookupMethodDeclarationWithConversionIgnoringParameter(
					"compareTo", parameterList, false, /*parameterToIgnore*/ null);
			if (null == compareTo) {
				self = new ObjectDeclaration("this", type, 0);
				parameterList.addFormalParameter(self);
				other = new ObjectDeclaration("other", type, 0);
				parameterList.addFormalParameter(other);
				compareTo = this.enclosedScope().lookupMethodDeclarationWithConversionIgnoringParameter(
						"compareTo", parameterList, false, /*parameterToIgnore*/ null);
				if (null == compareTo) {
					retval = false;
				} else {
					retval = true;
				}
			} else {
				retval = true;
			}
		}
		return retval;
	}
	public String pathName() {
		// pathName is just a unique String for this type. We
		// generate it as the linear catenation of the names
		// of all the scopes up to the top
		final StaticScope globalScope = StaticScope.globalScope();
		String retval = "";
		StaticScope scope = this.enclosedScope();
		while (scope != globalScope) {
			if (null == scope) {
				// might be a template. return something.
				retval = this.name();
				break;
			} else {
				final Declaration associatedDeclaration = scope.associatedDeclaration();
				if (null == associatedDeclaration) {
					// e.g. at global scope
					retval = this.name();
				} else {
					retval = associatedDeclaration.name() + "." + retval;
				}
				scope = scope.parentScope();
			}
		}
		return retval;
	}
	public static class ClassOrContextType extends Type {
		public ClassOrContextType(final String name, final StaticScope enclosedScope,
				final ClassType baseType) {
			super(enclosedScope);
			name_ = name;
			baseClass_ = baseType;
		}
		@Override public Type type() {
			return this;
		}
		@Override public String name() {
			return name_;
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			assert false;
			return false;
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			return this.canBeConvertedFrom(t);
		}
		public ClassType baseClass() {
			return baseClass_;
		}
		public void updateBaseType(final ClassType baseType) {
			baseClass_ = baseType;
		}
		
		private final String name_;
		private ClassType baseClass_;
	}

	public static class ClassType extends ClassOrContextType {
		public ClassType(final String name, final StaticScope enclosedScope, final ClassType baseClass) {
			super(name, enclosedScope, baseClass);
			interfaceTypes_ =  new ArrayList<InterfaceType>();
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			boolean retval = false;
			if (null == t || null == t.pathName() || null == pathName()) {
				assert false;
			} else if (name().equals("Object")) {
				// anything can be converted to Object
				retval = true;
			} else {
				retval = t.pathName().equals(pathName());
				if (!retval) {
					if (t.name().equals("Null")) {
						retval = true;
					} else if (null != isTemplate(this) && null != isTemplate(t)) {
						// Messy, messy, messy
						final String thisParameterName = isTemplate(this);
						final String tParameterName = isTemplate(t);
						Type thisType = enclosedScope().lookupTypeDeclarationRecursive(thisParameterName);
						if (null == thisType) {
							if (this.name().startsWith("List<")) {
								final StaticScope enclosedScope = this.enclosedScope();
								final TemplateInstantiationInfo templateInstantiationInfo = enclosedScope.templateInstantiationInfo();
								thisType = templateInstantiationInfo.classSubstitionForFormalParameterNamed(thisParameterName);
							}
						}
						Type tType = enclosedScope().lookupTypeDeclarationRecursive(tParameterName);
						if (null == tType) {
							if (t.name().startsWith("List<")) {
								final StaticScope enclosedScope = t.enclosedScope();
								final TemplateInstantiationInfo templateInstantiationInfo = enclosedScope.templateInstantiationInfo();
								tType = templateInstantiationInfo.classSubstitionForFormalParameterNamed(tParameterName);
							}
						}
						assert null != tType;
						assert null != thisType;
						retval = thisType.canBeConvertedFrom(tType);
					} else if (t instanceof ClassType || t instanceof ContextType) {
						// Base class check
						final ClassOrContextType classyT = (ClassOrContextType)t;
						for (ClassType aBase = classyT.baseClass(); null != aBase; aBase = aBase.baseClass()) {
							if (aBase.name().equals(name())) {
								retval = true;
								break;
							}
						}
					}
				}
			}
			return retval;
		}
		@Override public boolean isBaseClassOf(final Type aDerived) {
			// IMPROPER base class!!
			boolean retval = false;
			if (aDerived instanceof ClassType) {
				final ClassType aClassyDerived = (ClassType)aDerived;
				for (ClassType aBase = aClassyDerived; null != aBase; aBase = aBase.baseClass()) {
					if (aBase.enclosedScope() == this.enclosedScope()) {
						retval = true;
						break;
					}
				}
			}
			return retval;
		}
		public void elaborateFromTemplate(final TemplateDeclaration templateDeclaration, final ClassType baseClass,
				final StaticScope newEnclosedScope, final Declaration newAssociatedDeclaration) {
			super.updateBaseType(baseClass);
			final TemplateType nominalType = (TemplateType)templateDeclaration.type();
			assert null != newEnclosedScope.parentScope();
			enclosedScope_ = newEnclosedScope;
			staticObjectDeclarationDictionary_ = new LinkedHashMap<String, ObjectDeclaration>();
			for (Map.Entry<String,ObjectDeclaration> iter : nominalType.staticObjectDeclarationDictionary_.entrySet()) {
				final String name = iter.getKey();
				final ObjectDeclaration decl = iter.getValue();
				final ObjectDeclaration declCopy = decl.copy();
				staticObjectDeclarationDictionary_.put(new String(name), declCopy);
			}
			
			staticObjects_ = new LinkedHashMap<String, RTCode>();
			for (final Map.Entry<String,RTCode> iter : nominalType.staticObjects_.entrySet()) {
				final String name = iter.getKey();
				final RTCode programElement = iter.getValue();
				
				// We really should do a copy here of program element, but
				// we'll presume that code isn't modified and cross our
				// fingers. Adding a dup() function to that class hierarchy
				// would be quite a chore...
				final String stringCopy = name.substring(0, name.length() - 1);
				staticObjects_.put(stringCopy, programElement);
			}
		}
		public final TemplateInstantiationInfo templateInstantiationInfo() {
			return enclosedScope_.templateInstantiationInfo();
		}
		public void addInterfaceType(final InterfaceType it) {
			interfaceTypes_.add(it);
		}
		public final List<InterfaceType> interfaceTypes() {
			return interfaceTypes_;
		}
		@Override public boolean canBeLhsOfBinaryOperatorForRhsType(final String operator, final Type type) {
			// ClassType
			assert true;		// tests/new_luhnvalidation.k
			boolean retval = false;
			if (this.type().canBeConvertedFrom(type)) {
				retval = true;
			} else {
				// Can always compare with null
				if (operator.equals("==") && type.pathName().equals("Null")) {
					retval = true;
				} else if (operator.equals("!=") && type.pathName().equals("Null")) {
					retval = true;
				}
			}
			return retval;
		}
		private String isTemplate(final Type t) {
			// Returns the template parameter or null
			String retval = null;
			final String typeName = t.name();
			
			// For now, just deal with Lists. We'll have to deal with Maps later
			if (typeName.startsWith("List<")) {
				if (typeName.matches("[a-zA-Z]<.*>") || typeName.matches("[A-Z][a-zA-Z0-9_]*<.*>")) {
					final int indexOfDelimeter = typeName.indexOf('<');
					final int l = typeName.length();
					retval = typeName.substring(indexOfDelimeter + 1, l - 1);
				}
			}
			
			return retval;
		}
		
		private List<InterfaceType> interfaceTypes_;
	}
	public static class TemplateType extends Type {
		public TemplateType(final String name, final StaticScope scope, final ClassType baseClass) {
			super(scope);
			baseClass_ = baseClass;
			name_ = name;
		}
		@Override public String name() {
			return name_;
		}
		public ClassType baseClass() {
			return baseClass_;
		}
		public void updateBaseType(final ClassType baseType) {
			baseClass_ = baseType;
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			return true;
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			return true;
		}
		@Override public Type type() {
			return this;
		}
		@Override public boolean isBaseClassOf(final Type aDerived) {
			return false;
		}
		
		private final String name_;
		private ClassType baseClass_;
	}
	public static class ContextType extends ClassOrContextType {
		public ContextType(final String name, final StaticScope scope, final ClassType baseClass) {
			super(name, scope, baseClass);
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			boolean retval = t.name().equals(name());
			if (!retval) {
				retval = t.name().equals("Null");
			}
			return retval;
		}
	}
	public static class InterfaceType extends Type {
		public InterfaceType(final String name, final StaticScope enclosedScope) {
			super(enclosedScope);
			name_ = name;
			selectorSignatureMap_ = new LinkedHashMap<String, List<MethodSignature>>();
		}
		@Override public String name() {
			return name_;
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			return this.canBeConvertedFrom(t);
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			boolean retval = t.name().equals(name_);
			if (!retval) {
				if (t.name().equals("Null")) {
					retval = true;
				} else if (t instanceof ClassType) {
					final ClassType classyT = (ClassType)t;
					for (final InterfaceType it : classyT.interfaceTypes()) {
						if (it.pathName().equals(pathName())) {
							retval = true;
							break;
						}
					}
				}
			}
			
			if (retval == false) {
				// See if all of my operations are supported by t
				retval = true;
				for (final String methodName : this.selectorSignatureMap().keySet()) {
					final List<MethodSignature> signatures = this.selectorSignatureMap().get(methodName);
					for (final MethodSignature interfaceSignature : signatures) {
						// Maybe there is a fight here to be had about whether the method
						// has public access...
						MethodDeclaration otherTypesMethod = t.enclosedScope().lookupMethodDeclarationWithConversionIgnoringParameter(
								methodName, interfaceSignature.formalParameterList(), false, /*parameterToIgnore*/ "this");
						if (null == otherTypesMethod) {
							if (t instanceof RoleType || t instanceof StagePropType) {
								// Then methods in the requires interface also apply. As long as
								// the object respond to the method, we don't care if if it's
								// part of the Role "public" interface or the Role / object
								// interface. There is no "public" or "private" here, so we'll
								// be generous
								final RoleType roleType = (RoleType)t;
								otherTypesMethod = ((StaticRoleScope)roleType.enclosedScope()).
										lookupRequiredMethodSelectorAndParameterList(methodName, interfaceSignature.formalParameterList());
								retval = (null != otherTypesMethod);
							} else {
								retval = false;
							}
							break;
						}
					}
				}
			}
			return retval;
		}
		@Override public Type type() {
			return this;
		}
		@Override public MethodSignature signatureForMethodSelectorCommon(final String methodSelector, final MethodSignature methodSignature,
				final String paramToIgnore, final HierarchySelector baseClassSearch) {
			MethodSignature retval = null;
			
			assert true;		// ever called? Yup. spell_check2.k
			
			final FormalParameterList methodSignatureFormalParameterList = methodSignature.formalParameterList();
			
			List<MethodSignature> signatures = null;
			if (selectorSignatureMap_.containsKey(methodSelector)) {
				signatures = selectorSignatureMap_.get(methodSelector);
				for (final MethodSignature signature : signatures) {
					if (FormalParameterList.alignsWithParameterListIgnoringParamNamed(
							signature.formalParameterList(),
							methodSignatureFormalParameterList,
							paramToIgnore, /*conversionAllowed*/ true)) {
						retval = signature;
						break;
					}
				}
			} else {
				retval = null;
			}
			
			return retval;
		}
		
		public MethodSignature lookupMethodSignature(final String selectorName, final ActualOrFormalParameterList argumentList) {
			return lookupMethodSignatureCommon(selectorName, argumentList, false, null);
		}
		public MethodSignature lookupMethodSignatureWithConversion(final String selectorName, final ActualOrFormalParameterList argumentList) {
			return lookupMethodSignatureCommon(selectorName, argumentList, true, null);
		}
		public MethodSignature lookupMethodSignatureWithConversionIgnoringParameter(final String selectorName,
				final ActualOrFormalParameterList argumentList, final String parameterToIgnore) {
			return lookupMethodSignatureCommon(selectorName, argumentList, true, parameterToIgnore);
		}

		public MethodSignature lookupMethodSignatureCommon(final String selectorName,
				final ActualOrFormalParameterList argumentList, final boolean conversionAllowed,
				final String parameterToIgnore) {
			MethodSignature retval = null;
			List<MethodSignature> signatures = null;
			if (selectorSignatureMap_.containsKey(selectorName)) {
				signatures = selectorSignatureMap_.get(selectorName);
				for (final MethodSignature signature : signatures) {
					if (FormalParameterList.alignsWithParameterListIgnoringParamNamed(signature.formalParameterList(),
							argumentList, parameterToIgnore, conversionAllowed)) {
						retval = signature;
						break;
					}
				}
			} else {
				retval = null;
			}
			
			return retval;
		}
		
		public void addSignature(final MethodSignature signature) {
			List<MethodSignature> signatures = null;
			if (selectorSignatureMap_.containsKey(signature.name())) {
				signatures = selectorSignatureMap_.get(signature.name());
			} else {
				signatures = new ArrayList<MethodSignature>();
				selectorSignatureMap_.put(signature.name(), signatures);
			}
			signatures.add(signature);
		}
		
		public final Map<String, List<MethodSignature>> selectorSignatureMap() {
			return selectorSignatureMap_;
		}

		
		private final String name_;
		private final Map<String, List<MethodSignature>> selectorSignatureMap_;
	}
	public static class BuiltInType extends Type {
		public BuiltInType(final String name) {
			super(new StaticScope(StaticScope.globalScope()));
			name_ = name;
		}
		@Override public String name() {
			return name_;
		}
		@Override public boolean hasUnaryOperator(final String operator) {
			boolean retval = false;
			if (this.name().equals("int") || this.name().equals("Integer")) {
				if (operator.equals("-")) retval = true;
				else if (operator.equals("+")) retval = true;
				else if (operator.equals("--")) retval = true;
				else if (operator.equals("++")) retval = true;
			} else if (this.name().equals("double")) {
				if (operator.equals("-")) retval = true;
				else if (operator.equals("+")) retval = true;
				else if (operator.equals("--")) retval = true;
				else if (operator.equals("++")) retval = true;
			} else if (this.name().equals("boolean")) {
				if (operator.equals("!")) retval = true;
			}
			return retval;
		}
		@Override public boolean canBeLhsOfBinaryOperatorForRhsType(final String operator, final Type type) {
			// Built-in type
			assert true;
			boolean retval = false;
			if (type instanceof RoleType == false &&
					type instanceof StagePropType == false &&
					this.type().canBeConvertedFrom(type) == false) {
				retval = false;
			} else if (this.name().equals("int") || this.name().equals("Integer")) {
				if (operator.equals("-")) retval = true;
				else if (operator.equals("+")) retval = true;
				else if (operator.equals("*")) retval = true;
				else if (operator.equals("/")) retval = true;
				else if (operator.equals("%")) retval = true;
				else if (operator.equals("**")) retval = true;
				else if (operator.equals("<")) retval = true;
				else if (operator.equals("<=")) retval = true;
				else if (operator.equals(">")) retval = true;
				else if (operator.equals(">=")) retval = true;
				else if (operator.equals("==")) retval = true;
				else if (operator.equals("!=")) retval = true;
			} else if (this.name().equals("double") || this.name().equals("Double")) {
				if (operator.equals("-")) retval = true;
				else if (operator.equals("+")) retval = true;
				else if (operator.equals("*")) retval = true;
				else if (operator.equals("/")) retval = true;
				else if (operator.equals("%")) retval = false;
				else if (operator.equals("**")) retval = true;
				else if (operator.equals("<")) retval = true;
				else if (operator.equals("<=")) retval = true;
				else if (operator.equals(">")) retval = true;
				else if (operator.equals(">=")) retval = true;
				else if (operator.equals("==")) retval = true;
				else if (operator.equals("!=")) retval = true;
			} else if (this.name().equals("boolean")) {
				if (operator.equals("||")) retval = true;
				else if (operator.equals("&&")) retval = true;
				else if (operator.equals("^")) retval = true;
				else if (operator.equals("==")) retval = true;
				else if (operator.equals("!=")) retval = true;
			} else if (this.name().equals("String")) {
				if (operator.equals("+")) retval = true;
				else if (operator.equals("%")) retval = true;
				else if (operator.equals("-")) retval = true;
				else if (operator.equals("<")) retval = true;
				else if (operator.equals("<=")) retval = true;
				else if (operator.equals(">")) retval = true;
				else if (operator.equals(">=")) retval = true;
				else if (operator.equals("==")) retval = true;
				else if (operator.equals("!=")) retval = true;
			} else {
				// == and != are always Ok to test against NULL
				if (type.pathName().equals("Null")) {
					if (operator.equals("==")) retval = true;
					else if (operator.equals("!=")) retval = true;
				}
			}
			return retval;
		}
		@Override public boolean canBeRhsOfBinaryOperator(final String operator) {
			boolean retval = false;
			if (this.name().equals("int") || this.name().equals("Integer")) {
				if (operator.equals("-")) retval = true;
				else if (operator.equals("+")) retval = true;
				else if (operator.equals("*")) retval = true;
				else if (operator.equals("/")) retval = true;
				else if (operator.equals("%")) retval = true;
				else if (operator.equals("**")) retval = true;
				else if (operator.equals("<=")) retval = true;
				else if (operator.equals(">=")) retval = true;
				else if (operator.equals("<")) retval = true;
				else if (operator.equals(">")) retval = true;
				else if (operator.equals("!=")) retval = true;
				else if (operator.equals("==")) retval = true;
			} else if (this.name().equals("double") || this.name().equals("Double")) {
				if (operator.equals("-")) retval = true;
				else if (operator.equals("+")) retval = true;
				else if (operator.equals("*")) retval = true;
				else if (operator.equals("/")) retval = true;
				else if (operator.equals("%")) retval = false;
				else if (operator.equals("**")) retval = true;
				else if (operator.equals("<=")) retval = true;
				else if (operator.equals(">=")) retval = true;
				else if (operator.equals("<")) retval = true;
				else if (operator.equals(">")) retval = true;
				else if (operator.equals("!=")) retval = true;
				else if (operator.equals("==")) retval = true;
			} else if (this.name().equals("boolean")) {
				if (operator.equals("||")) retval = true;
				else if (operator.equals("&&")) retval = true;
				else if (operator.equals("^")) retval = true;
				else if (operator.equals("!=")) retval = true;
				else if (operator.equals("==")) retval = true;
			} else if (this.name().equals("String")) {
				if (operator.equals("+")) retval = true;
				else if (operator.equals("%")) retval = true;
				else if (operator.equals("-")) retval = true;
				else if (operator.equals("<=")) retval = true;
				else if (operator.equals(">=")) retval = true;
				else if (operator.equals("<")) retval = true;
				else if (operator.equals(">")) retval = true;
				else if (operator.equals("!=")) retval = true;
				else if (operator.equals("==")) retval = true;
			}
			return retval;
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			return canBeConvertedFrom(t);
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			boolean retval = false;
			
			// t can be null on error conditions, so stumble elegantly
			if (null != t) {
				if (t.name().equals(name_)) {
					retval = true;
				} else if (name().equals("double") && (t.name().equals("int") || t.name().equals("Integer"))) {
					retval = true;
				} else if ((t.name().equals("int") || t.name().equals("Integer")) && t.name().equals("double")) {
					// TODO: Issue truncation warning?
					retval = true;
				} else if (t.name().equals("Null")) {
					retval = true;
				} else if (t instanceof RoleType|| t instanceof StagePropType) {
					// Then make sure that everything in the "requires" list of
					// the this is supported by the Role type
					
					final RoleType roleType = (RoleType)t;
					final StaticScope roleScope = roleType.enclosedScope();
					final Declaration associatedDecl = roleScope.associatedDeclaration();
					assert associatedDecl instanceof RoleDeclaration;
					final List<MethodDeclaration> requiredMethodDecls = this.enclosedScope().methodDeclarations();
					if (this.pathName().equals("Null")) {
						// Can always have a null object
						retval = true;
					} else {
						retval = true;
						for (final MethodDeclaration declaration : requiredMethodDecls) {
							final String requiredMethodName = declaration.name();
							final MethodSignature requiredMethodSignature = declaration.signature();
							
							// Does the Role have it?
							final MethodDeclaration mdecl = roleScope.lookupMethodDeclarationWithConversionIgnoringParameter(
									requiredMethodName,
									requiredMethodSignature.formalParameterList(),
									/*ignoreSignature*/ false,
									/*parameterToIgnore*/ "this");
							
							if (null == mdecl) {
								retval = false;
								break;
							}
						}
					}
				} else if (t instanceof InterfaceType) {
					// Then make sure that everything in the "requires" list of
					// this is supported by the Interface type
					
					final InterfaceType interT = (InterfaceType) t;
					if (this.pathName().equals("Null")) {
						// Can always have a null object
						retval = true;
					} else {
						retval = true;
				
						// Loop through all interface declarations. Make sure that
						// I support them all
						final Map<String, List<MethodSignature>> interfaceSignatures = interT.selectorSignatureMap();
						final Set<String> methodNames = interfaceSignatures.keySet();
						for (final String requiredMethodName : methodNames) {
							final List<MethodSignature> signaturesForName = interfaceSignatures.get(requiredMethodName);
							for (final MethodSignature requiredMethodSignature : signaturesForName) {
								final MethodSignature msignature = interT.lookupMethodSignatureWithConversionIgnoringParameter(
										requiredMethodName,
										requiredMethodSignature.formalParameterList(),
										/*parameterToIgnore*/ "this");
								if (null == msignature) {
									retval = false;
									break;
								}
							}
						}
					}
				}
			}
			
			return retval;
		}
		@Override public Type type() {
			return this;
		}
		
		private final String name_;
	}
	public static class RoleType extends Type {
		public RoleType(final String name, final StaticScope scope) {
			super(scope);
			name_ = name;
		}
		public void reportMismatchesWith(final int lineNumber, final Type type) {
			// Make sure that each method in my "requires" signature
			// is satisfied in the signature of t
			
			if (type instanceof RoleType && type.pathName().equals(pathName())) {
				// it's just one of us...
				;
			} else {
				final Map<String, List<MethodSignature>> requiredSelfSignatures = associatedDeclaration_.requiredSelfSignatures();
				for (final String methodName : requiredSelfSignatures.keySet()) {
					final List<MethodSignature> rolesSignatures = requiredSelfSignatures.get(methodName);
					
					for (final MethodSignature rolesSignature : rolesSignatures) {
						final MethodSignature signatureForMethodSelector =
							type.signatureForMethodSelectorIgnoringThisWithPromotionAndConversion(methodName, rolesSignature);
						if (null == signatureForMethodSelector) {
							ErrorLogger.error(ErrorType.Fatal, lineNumber, "\t`",
									rolesSignature.name() + rolesSignature.formalParameterList().selflessGetText(),
									"' needed by Role `", name(),
									"' does not appear in interface of `", type.name() + "'.");
						} else if (signatureForMethodSelector.accessQualifier() != AccessQualifier.PublicAccess) {
							ErrorLogger.error(ErrorType.Fatal, lineNumber, "\t`",
									rolesSignature.name() + rolesSignature.formalParameterList().selflessGetText(),
									"' needed by Role `", name(),
									"' is declared as private in interface of `", type.name() +
									"' and is therefore inaccessible to the Role.");
						}
					}
				}
			}
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			return canBeConvertedFrom(t);
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			// Make sure that each method in my "requires" signature
			// is satisfied in the signature of t

			boolean retval = false;
			
			if (null != t && t.pathName().equals("Null")) {
				// Anything can be associated with a null object
				retval = true;
			} else if (t instanceof RoleType && t.pathName().equals(pathName())) {
				// it's just one of us...
				retval = true;
			} else if (null != associatedDeclaration_){
				final Map<String, List<MethodSignature>> requiredSelfSignatures = associatedDeclaration_.requiredSelfSignatures();
				retval = true;
				for (final Map.Entry<String, List<MethodSignature>> entry : requiredSelfSignatures.entrySet()) {
					final String methodName = entry.getKey();
					final List<MethodSignature> rolesSignatures = entry.getValue();
					
					// It's doubtful there are many, but it's possible. Cover them all.
					for (final MethodSignature rolesSignature : rolesSignatures) {
						final MethodSignature signatureForMethodSelector = null != t?
								t.signatureForMethodSelectorInHierarchyIgnoringThis(methodName, rolesSignature):
								null;
						if (null == signatureForMethodSelector) {
							// See if RHS is itself a Role / StageProp, and compare
							if (null != t && (t instanceof RoleType || t instanceof StagePropType)) {
								final RoleType otherAsRole = (RoleType)t;
								final Map<String, List<MethodSignature>> otherSignatures = otherAsRole.associatedDeclaration().requiredSelfSignatures();
								
								// Any one of the "otherSignatures" will do if it matches
								boolean found = false;
								final List<MethodSignature> appearancesOfThisSignatureInOther = otherSignatures.get(methodName);
								for (final MethodSignature possibleMatchinSignature : appearancesOfThisSignatureInOther) {
									final FormalParameterList myParameterList = rolesSignature.formalParameterList();
									final FormalParameterList otherArgumentList = possibleMatchinSignature.formalParameterList();
									if (FormalParameterList.alignsWithParameterListIgnoringRoleStuff(myParameterList, otherArgumentList, true)) {
										found = true;
										break;
									}
								}
								retval = found;
								break;
							} else {
								retval = false;
								break;
							}
						} else {
							if (signatureForMethodSelector.accessQualifier() != AccessQualifier.PublicAccess) {
								// It would violate encapsulation of the object by the Role
								// if Role scripts could invoke private scripts of their Role-player
								retval = false;
							} else {
								retval = true;
							}
							break;
						}
					}

					if (false == retval) break;
				}
			} else {
				// associatedDeclaration_ is null?
				// assert false;
				retval = false;	// probably just a Pass1 stumble. chainable1.k
			}
			return retval;
		}
		@Override public boolean canBeLhsOfBinaryOperatorForRhsType(final String operator, final Type type) {
			// RoleType
			assert true;
			boolean retval;
			if ((operator.equals("==") || operator.equals("!=")) && type.pathName().equals("Null")) {
				// Can always compare with null
				retval = true;
			} else if (operator.equals(">") || operator.equals(">=") || operator.equals("<") || operator.equals("<=") ||
					operator.equals("==") || operator.equals("!=")) {
				retval = this.hasCompareToOrHasOperatorForArgOfType(operator, type);
			} else {
				retval = false;
			}
			return retval;
		}
		@Override public String name() {
			return name_;
		}
		public void setBacklinkToRoleDecl(final RoleDeclaration roleDecl) {
			associatedDeclaration_ = roleDecl;
		}
		public RoleDeclaration associatedDeclaration() {
			assert true;	// ever called? yes.
			return associatedDeclaration_;
		}
		@Override public Type type() {
			return this;
		}
		public StaticScope enclosingScope() {
			return this.enclosedScope().parentScope();
		}
		public MethodSignature lookupMethodSignatureDeclaration(final String methodName) {
			return associatedDeclaration_.lookupMethodSignatureDeclaration(methodName);
		}
		public Declaration contextDeclaration() {
			return associatedDeclaration_.contextDeclaration();
		}
		public boolean isArray() {
			return associatedDeclaration_ instanceof RoleArrayDeclaration ||
				   associatedDeclaration_ instanceof StagePropArrayDeclaration;
		}
		public boolean hasCompareToOrHasOperatorForArgOfType(final String operator, final Type rhs) {
			boolean retval = false;
			final Map<String, List<MethodSignature>> requiredSelfSignatures = associatedDeclaration_.requiredSelfSignatures();
			for (final String methodName : requiredSelfSignatures.keySet()) {
				final List<MethodSignature> requiredSignatureList = requiredSelfSignatures.get(methodName);
				if (methodName.equals("compareTo") || methodName.equals(operator)) {
					for (final MethodSignature rolesSignature: requiredSignatureList) {
						final FormalParameterList formalParameters = rolesSignature.formalParameterList();
						if (formalParameters.count() > 1) {
							final ObjectDeclaration wannabeRhsArg = formalParameters.parameterAtPosition(1);
							final Type argType = wannabeRhsArg.type();
							if (rhs.pathName().equals(argType.pathName())) {
								retval = true;
								break;
							}
						}
					}
				}
			}
			return retval;
		}
		
		protected final String name_;
		protected RoleDeclaration associatedDeclaration_;
	}
	
	public static class StagePropType extends RoleType {
		public StagePropType(final String name, final StaticScope scope) {
			super(name, scope);
		}
		public void reportMismatchesWith(final int lineNumber, final Type type) {
			// Make sure that each method in my "requires" signature
			// is satisfied in the signature of t
			
			if (type instanceof StagePropType && type.pathName().equals(pathName())) {
				// it's just one of us...
				;
			} else {
				final Map<String, List<MethodSignature>> requiredSelfSignatures = associatedDeclaration_.requiredSelfSignatures();
				for (final String methodName : requiredSelfSignatures.keySet()) {
					final List<MethodSignature> rolesSignatures = requiredSelfSignatures.get(methodName);
					
					for (final MethodSignature rolesSignature : rolesSignatures) {
						final MethodSignature signatureForMethodSelector =
							type.signatureForMethodSelectorIgnoringThisWithPromotionAndConversion(methodName, rolesSignature);
						if (null == signatureForMethodSelector) {
							ErrorLogger.error(ErrorType.Fatal, lineNumber, "\t`",
									rolesSignature.name() + rolesSignature.formalParameterList().selflessGetText(),
									"' needed by Stage Prop `", name(),
									"' does not appear in interface of `", type.name() + "'.");
						} else if (signatureForMethodSelector.accessQualifier() != AccessQualifier.PublicAccess) {
							ErrorLogger.error(ErrorType.Fatal, lineNumber, "\t`",
									rolesSignature.name() + rolesSignature.formalParameterList().selflessGetText(),
									"' needed by Stage Prop `", name(),
									"' is declared as private in interface of `", type.name() +
									"' and is therefore inaccessible to the Stage Prop.");
						}
					}
				}
			}
		}
		public Map<String, List<MethodSignature>> requiredSelfSignatures() {
			return associatedDeclaration_.requiredSelfSignatures();
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			// Make sure that each method in my "requires" signature
			// is satisfied in the signature of t

			boolean retval = false;
			
			if (null != t && t.pathName().equals("Null")) {
				// Anything can be associated with a null object
				retval = true;
			} else if (t instanceof RoleType && t.name().equals(name())) {
				// it's just one of us...
				retval = true;
			} else if (null != associatedDeclaration_){
				final Map<String, List<MethodSignature>> requiredSelfSignatures = associatedDeclaration_.requiredSelfSignatures();
				retval = true;
				for (final Map.Entry<String, List<MethodSignature>> entry : requiredSelfSignatures.entrySet()) {
					final String methodName = entry.getKey();
					final List<MethodSignature> rolesSignatures = entry.getValue();
					
					// It's doubtful there are many, but it's possible. Cover them all.
					for (final MethodSignature rolesSignature : rolesSignatures) {
						final MethodSignature signatureForMethodSelector = null != t?
								t.signatureForMethodSelectorInHierarchyIgnoringThis(methodName, rolesSignature):
								null;
						if (null == signatureForMethodSelector) {
							// See if RHS is itself a Role / StageProp, and compare
							if (t instanceof RoleType || t instanceof StagePropType) {
								final RoleType otherAsRole = (RoleType)t;
								final Map<String, List<MethodSignature>> otherSignatures = otherAsRole.associatedDeclaration().requiredSelfSignatures();
								
								// Any one of the "otherSignatures" will do if it matches
								boolean found = false;
								final List<MethodSignature> appearancesOfThisSignatureInOther = otherSignatures.get(methodName);
								for (final MethodSignature possibleMatchinSignature : appearancesOfThisSignatureInOther) {
									final FormalParameterList myParameterList = rolesSignature.formalParameterList();
									final FormalParameterList otherArgumentList = possibleMatchinSignature.formalParameterList();
									if (FormalParameterList.alignsWithParameterListIgnoringRoleStuff(myParameterList, otherArgumentList, true)) {
										found = true;
										break;
									}
								}
								retval = found;
							} else {
								retval = false;
							}
							break;
						} else if (signatureForMethodSelector.accessQualifier() != AccessQualifier.PublicAccess) {
							// It would violate encapsulation of the object by the Role
							// if Role scripts could invoke private scripts of their Role-player
							retval = false;
							break;
						} else if (signatureForMethodSelector.hasConstModifier() == false) {
							final String roleSignatureLineNumber = Integer.toString(signatureForMethodSelector.lineNumber()) +
									" does not match the contract at line " +
									Integer.toString(rolesSignature.lineNumber());
							parserPass.errorHook5p2(ErrorType.Warning, lineNumber,
									"WARNING: Required methods for stage props should be const. The declaration of method ",
									signatureForMethodSelector.name(), " at line ",
									roleSignatureLineNumber);
							break;
						}
					}

					if (false == retval) break;
				}
			} else {
				// associatedDeclaration_ is null?
				// assert false;
				retval = false;	// probably just a Pass1 stumble. chainable1.k
			}
			return retval;
		}

		@Override public boolean canBeConvertedFrom(final Type t) {
			// Make sure that each method in my "requires" signature
			// is satisfied in the signature of t

			boolean retval = false;
			if (t instanceof StagePropType && t.name().equals(name())) {
				// it's just one of us...
				retval = true;
			} else if (t.pathName().equals("Null")) {
				// can always compare with Null
				retval = true;
			} else {
				retval = true;
				
				final Map<String, List<MethodSignature>> requiredSelfSignatures = associatedDeclaration_.requiredSelfSignatures();
				
				// Must cover them all
				for (final String methodName : requiredSelfSignatures.keySet()) {
					final List<MethodSignature> roleSignatures = requiredSelfSignatures.get(methodName);
					
					// Have to cover all of them
					for (final MethodSignature rolesSignature : roleSignatures) {
						final MethodSignature signatureForMethodSelector =
								t.signatureForMethodSelectorInHierarchyIgnoringThis(methodName, rolesSignature);
						if (null == signatureForMethodSelector) {
							retval = lookForRoleRHSThatMatches(t, methodName, rolesSignature);
						} else if (signatureForMethodSelector.hasConstModifier() == false) {
							// Now handled by error-reporting version above...
							// final String roleSignatureLineNumber = Integer.toString(rolesSignature.lineNumber());
							// ErrorLogger.error(ErrorType.Warning, signatureForMethodSelector.lineNumber(),
							// 		"Required methods for stage props should be const. The declaration of method ",
							//		signatureForMethodSelector.name(), " does not match the contract at line ", roleSignatureLineNumber);
						}
						
						if (false == retval) break;
					}
					
					if (false == retval) break;
				}
			}
			return retval;
		}
		
		private boolean lookForRoleRHSThatMatches(final Type t, final String methodName,
				final MethodSignature lhsRoleSignature) {
			// See if RHS is itself a Role / StageProp, and compare
			
			boolean retval = false;
			if (t instanceof RoleType || t instanceof StagePropType) {
				final RoleType otherAsRole = (RoleType)t;
				final Map<String, List<MethodSignature>> otherSignatures = otherAsRole.associatedDeclaration().requiredSelfSignatures();
				final List<MethodSignature> appearancesOfThisSignatureInOther = otherSignatures.get(methodName);
				
				// Any one will do
				for (final MethodSignature appearanceOfThisSignatureInOther : appearancesOfThisSignatureInOther) {
					final FormalParameterList myParameterList = lhsRoleSignature.formalParameterList();
					if (null != appearanceOfThisSignatureInOther) {
						final FormalParameterList otherArgumentList = appearanceOfThisSignatureInOther.formalParameterList();
						if (FormalParameterList.alignsWithParameterListIgnoringRoleStuff(myParameterList, otherArgumentList, true)) {
							if (appearanceOfThisSignatureInOther.hasConstModifier()) {
								retval = true;
								break;
							}
						}
					}
				}
			} else {
				retval = false;
			}
			return retval;
		}
		
		@Override public boolean canBeLhsOfBinaryOperatorForRhsType(final String operator, final Type rhsType) {
			// This is a bit dicey — it really should be type-checked
			// by the caller as well. This doesn't amount to much more
			// than a syntactic check, since Roles have a decidely macro-
			// like behaviour to them..
			//
			// Check out caller: Pass2Listener.binopTypeCheck(Expression, String, Expression, Token) line: 441	
			// StageProp Type
			assert true;	// money_transfer3.k
			boolean retval;
			if (operator.equals("+") || operator.equals("-") || operator.equals("/") || operator.equals("*")) {
				retval = true;
			} else {
				if (hasCompareToOrHasOperatorForArgOfType(operator, rhsType) && operator.equals(">") || operator.equals("<") || operator.equals(">=") ||
						operator.equals("<=") || operator.equals("!=") || operator.equals("==")) {
					retval = true;
				} else {
					retval = false;
				}
			}
			return retval;
		}
	}
	
	public static class ArrayType extends Type implements IndexableType {
		public ArrayType(final String name, final Type baseType) {
			super(null);
			name_ = name;
			sizeMethodDeclaration_ = null;
			baseType_ = baseType;
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			return canBeConvertedFrom(t);
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			boolean retval = true;
			if (t.name().equals("Null")) {
				// redundant but clear
				retval = true;
			} else if (t instanceof ArrayType == false) {
				retval = false;
			} else {
				final ArrayType tAsArray = (ArrayType) t;
				retval = baseType_.canBeConvertedFrom(tAsArray.baseType());
			}
			return retval;
		}
		@Override public String name() {
			return name_;
		}
		@Override public Type type() {
			return this;
		}
		public Type baseType() {
			return baseType_;
		}
		
		// This is kind of a hack because arrays as objects don't
		// have that many methods and they really don't have
		// their own scope where we can declare things like this.
		public MethodDeclaration sizeMethodDeclaration(final StaticScope enclosingScope) {
			if (null == sizeMethodDeclaration_) {
				final ObjectDeclaration self = new ObjectDeclaration("this", this, 0);
				final FormalParameterList formalParameterList = new FormalParameterList();
				formalParameterList.addFormalParameter(self);
				final MethodSignature signature = new MethodSignature("size",
						StaticScope.globalScope().lookupTypeDeclaration("int"),
						AccessQualifier.PublicAccess, 0, false);
				signature.addParameterList(formalParameterList);
				dummyScope_ = new StaticScope(enclosingScope);
				sizeMethodDeclaration_ = new MethodDeclaration(signature, dummyScope_, 0);
				dummyScope_.declareMethod(sizeMethodDeclaration_);
			}
			return sizeMethodDeclaration_;
		}
		
		private final Type baseType_;
		private final String name_;
		private MethodDeclaration sizeMethodDeclaration_;
		private StaticScope dummyScope_;
	}
	
	public static class TemplateParameterType extends Type {
		public TemplateParameterType(final String name, final ClassType baseClassType) {
			super(null);
			name_ = name;
			baseClassType_ = baseClassType;
		}
		@Override public boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass) {
			return true;
		}
		@Override public boolean canBeConvertedFrom(final Type t) {
			return true;
		}
		@Override public String name() {
			return name_;
		}
		@Override public Type type() {
			return this;
		}
		public ClassType baseClassType() {
			return baseClassType_;
		}
		
		private final ClassType baseClassType_;
		private final String name_;
	}
	
	
	public abstract boolean canBeConvertedFrom(final Type t);
	public abstract boolean canBeConvertedFrom(final Type t, final int lineNumber, final Pass1Listener parserPass);

	public boolean hasUnaryOperator(final String operator) {
		return false;
	}
	public boolean canBeRhsOfBinaryOperator(final String operator) {
		return false;
	}
	public String getText() {
		return this.name();
	}
	public RTCode getStaticObject(final String name) {
		assert staticObjects_.containsKey(name);
		return staticObjects_.get(name);
	}
	public MethodSignature signatureForMethodSelectorCommon(final String methodSelector, final MethodSignature methodSignature,
			final String paramToIgnore, final HierarchySelector baseClassSearch) {
		final FormalParameterList methodSignatureFormalParameterList = methodSignature.formalParameterList();
		if (null == enclosedScope_) {
			assert null != enclosedScope_;
		}
		final MethodDeclaration mDecl = /*class*/enclosedScope_.lookupMethodDeclarationIgnoringParameter(methodSelector, methodSignatureFormalParameterList, paramToIgnore,
				/* conversionAllowed = */ false);
		
		// mDecl can be null under error conditions
		MethodSignature retval = null == mDecl? null: mDecl.signature();
	
		// See if we need to explore base class signatures
		if (null == retval && baseClassSearch == HierarchySelector.AlsoSearchBaseClass) {
			if (this instanceof ClassType) {
				final ClassType classType = (ClassType) this;
				if (null != classType.baseClass()) {
					// Recur. Good code reuse.
					retval = classType.baseClass().signatureForMethodSelectorCommon(methodSelector, methodSignature,
							paramToIgnore, baseClassSearch);
				} else {
					retval = null;	// redundant
				}
			} else {
				retval = null;	// redundant
			}
		} else {
			;	// is O.K.
		}
		
		return retval;
	}
	public MethodSignature signatureForMethodSelectorGeneric(final String methodSelector, final MethodSignature methodSignature) {
		final FormalParameterList methodSignatureFormalParameterList = methodSignature.formalParameterList();
		if (null == enclosedScope_) {
			assert null != enclosedScope_;
		}
		final MethodDeclaration mDecl = /*class*/enclosedScope_.lookupMethodDeclarationIgnoringParameter(methodSelector,
				methodSignatureFormalParameterList,
				/* paramToIgnore */ "this",
				/* conversionAllowed = */ true);
		
		// mDecl can be null under error conditions
		MethodSignature retval = null == mDecl? null: mDecl.signature();
	
		// We need to explore base class signatures
		if (null == retval) {
			if (this instanceof ClassType) {
				final ClassType classType = (ClassType) this;
				if (null != classType.baseClass()) {
					// Recur. Good code reuse.
					retval = classType.baseClass().signatureForMethodSelectorGeneric(methodSelector, methodSignature);
				} else {
					retval = null;	// redundant
				}
			} else {
				retval = null;	// redundant
			}
		}
		
		return retval;
	}
	public MethodSignature signatureForMethodSelectorInHierarchyIgnoringThis(final String methodSelector, final MethodSignature methodSignature) {
		return signatureForMethodSelectorCommon(methodSelector, methodSignature, "this",
				HierarchySelector.AlsoSearchBaseClass);
	}
	public MethodSignature signatureForMethodSelectorIgnoringThis(final String methodSelector, final MethodSignature methodSignature) {
		return signatureForMethodSelectorCommon(methodSelector, methodSignature, "this",
				HierarchySelector.ThisClassOnly);
	}
	public MethodSignature signatureForMethodSelectorIgnoringThisWithPromotion(final String methodSelector, final MethodSignature methodSignature) {
		return signatureForMethodSelectorCommon(methodSelector, methodSignature, "this",
				HierarchySelector.AlsoSearchBaseClass);
	}
	public MethodSignature signatureForMethodSelectorIgnoringThisWithPromotionAndConversion(final String methodSelector, final MethodSignature methodSignature) {
		return signatureForMethodSelectorGeneric(methodSelector, methodSignature);
	}
	public MethodSignature signatureForMethodSelector(final String methodSelector, final MethodSignature methodSignature) {
		return signatureForMethodSelectorCommon(methodSelector, methodSignature, null,
				HierarchySelector.ThisClassOnly);
	}
	public int lineNumber() {
		return lineNumber_;
	}
	
	protected StaticScope enclosedScope_;
	protected Map<String, ObjectDeclaration> staticObjectDeclarationDictionary_;
	protected Map<String, RTCode> staticObjects_;
	private final int lineNumber_;
}
