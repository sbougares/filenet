package com.tn.filenet.subs;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.filenet.api.admin.Choice;
import com.filenet.api.admin.LocalizedString;
import com.filenet.api.collection.ChoiceList;
import com.filenet.api.collection.LocalizedStringList;
import com.filenet.api.constants.AutoUniqueName;
import com.filenet.api.constants.ChoiceType;
import com.filenet.api.constants.DefineSecurityParentage;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.core.Document;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.engine.EventActionHandler;
import com.filenet.api.events.ObjectChangeEvent;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.meta.ClassDescription;
import com.filenet.api.meta.PropertyDescription;
import com.filenet.api.property.Properties;
import com.filenet.api.util.Id;

public class PropertyBasedFolderer implements EventActionHandler {
	private static final String NULLABLE = "nullable.";
	private static final String NAME = "Name";
	private static final DateFormat dateFormat=new SimpleDateFormat("yyyyMMddHHmmss");
	private static final String CHOICE_LOCALE = "choiceLocale";
	private static final String RULE = "rule.";
	private static final String FOLDER = "Folder";
	private static final String COM_INTERVALLE_FILENET_SUBS_FOLDERINGRULES = "com.intervalle.filenet.subs.folderingrules";
	private static Map<String, Map<String, ClassConfig>> classRules = new HashMap<String, Map<String, ClassConfig>>();

	private static class ClassConfig {
		public List<List<PropertyEvaluator>> evaluators=new ArrayList<List<PropertyEvaluator>>();
		public List<String> nullableProps=new ArrayList<String>();
	}
	
	@Override
	public void onEvent(ObjectChangeEvent objectchangeevent, Id id)
			throws EngineRuntimeException {

		Document doc = (Document) objectchangeevent.get_SourceObject();

		ClassConfig classConfig;
		try {
			classConfig = getClassRules(doc.getObjectStore()).get(doc.getClassName());
		} catch (Exception e) {
			throw new EngineRuntimeException(e,
					ExceptionCode.EVENT_HANDLER_THREW, null);
		}

		if (classConfig == null || classConfig.evaluators.size() == 0)
			return;

		for (List<PropertyEvaluator> rule : classConfig.evaluators) {
			String path;
			try {
				path = evaluatePath(doc.getProperties(), rule,classConfig.nullableProps);
			} catch (Exception e) {
				throw new EngineRuntimeException(e,
						ExceptionCode.EVENT_HANDLER_THREW, null);
			}
			
			Folder folder = null;
			try {
				folder = Factory.Folder.fetchInstance(doc.getObjectStore(),
						path, null);
			} catch (Exception e) {
			}

			if (folder == null) {
				while (path.startsWith("/"))
					path = path.substring(1);

				String[] pathParts = path.split("/");

				folder = Factory.Folder.fetchInstance(doc.getObjectStore(),
						"/", null);

				String pathSoFar="";
				for (String pathPart : pathParts) {
					pathPart=pathPart.trim();
					pathSoFar+="/"+pathPart;

					Folder f = null;
					try {
						f = Factory.Folder.fetchInstance(doc.getObjectStore(),
								pathSoFar, null);
					} catch (Exception e) {
					}
					
					if (f == null) {
						f = Factory.Folder.createInstance(doc.getObjectStore(),
								FOLDER);
						f.set_FolderName(pathPart);
						f.set_Parent(folder);
						f.save(RefreshMode.NO_REFRESH);
					}

					folder = f;
				}
			}

			String folderingName=doc.getProperties().isPropertyPresent(NAME)?doc.get_Name():null;
			if(folderingName!=null) folderingName=replaceIllegalChars(folderingName);
			
			folder.file(doc, AutoUniqueName.AUTO_UNIQUE, folderingName,
					DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE)
					.save(RefreshMode.NO_REFRESH);
		}

	}

	private static void validatePartialPath(String path) throws Exception {
		if (path == null || path.trim().length() == 0)
			throw new Exception("Path part cannot be empty!");
	}

	private static void validatePath(String path) throws Exception {
		validatePartialPath(path);

		if (!path.startsWith("/"))
			throw new Exception("Path must start with '/'!");
	}

	public static interface PropertyEvaluator {
		String evaluate(Properties props,List<String> nullables) throws Exception;
	}

	public static class Constant implements PropertyEvaluator {
		private String constant;

		public Constant(String constant) throws Exception {
			validatePartialPath(constant);
			this.constant = constant;
		}

		@Override
		public String evaluate(Properties props,List<String> nullables) {
			return constant;
		}
	}

	public static class PropertyRegexp implements PropertyEvaluator {
		private String property;
		private Pattern pattern;
		private int ordinal;
		private Map<String, String> allChoices;

		public PropertyRegexp(String property, Map<String, String> allChoices,
				String regex, int ordinal) throws Exception {
			this.property = property;
			this.pattern = Pattern.compile(regex);

			if (ordinal < 0)
				throw new Exception("Ordinal cannot be less than 0!");
			this.ordinal = ordinal;
			this.allChoices = allChoices;

		}

		@Override
		public String evaluate(Properties props,List<String> nullables) throws Exception {
			Object propValue = null;
			if (!props.isPropertyPresent(property)
					|| (propValue = props.getObjectValue(property)) == null) {
			
				if(nullables==null||!nullables.contains(property))
					throw new Exception(String.format("Property %s cannot be null",
						property));
				else return "";
			}

			String propValueString = (propValue instanceof Date)?dateFormat.format((Date)propValue):propValue.toString();

			if(propValueString!=null) propValueString=replaceIllegalChars(propValueString);

			if (allChoices != null && allChoices.containsKey(propValueString))
				propValueString = allChoices.get(propValueString);

			Matcher matcher = pattern.matcher(propValueString);
			if (!matcher.matches())
				throw new Exception(String.format(
						"Property (%s: %s) does not match regex: %s", property,
						propValueString, pattern.pattern()));

			if (matcher.groupCount() < ordinal)
				throw new Exception(
						String.format(
								"Property (%s: %s) does not have %d groups in regex: %s",
								property, propValueString, ordinal,
								pattern.pattern()));

			String ret = matcher.group(ordinal);
			validatePartialPath(ret);

			return ret;
		}
	}

	private static String evaluatePath(Properties props,
			List<PropertyEvaluator> evaluators,List<String> nullables) throws Exception {
		String ret = "";
		for (PropertyEvaluator evaluator : evaluators) {
			ret += evaluator.evaluate(props,nullables);
		}
		
		int iofds;
		while((iofds=ret.indexOf("//"))>=0)ret=ret.substring(0,iofds)+ret.substring(iofds+1);
		
		

		validatePath(ret);

		return ret;
	}

	private static List<PropertyEvaluator> parsePathExpression(ObjectStore os,
			String className, String path,String locale) throws Exception {
		List<String> tokens = new ArrayList<String>();

		int currEvalStart = 0;
		while (path.length() > 0) {
			int iofp = path.indexOf('\\', currEvalStart);

			if (iofp < 0) {
				tokens.add(path);
				path = "";
			} else if (iofp == (path.length() - 1)) {
				throw new Exception(
						"Single \\ characters are not allowed at he end of line!");
			} else if (path.charAt(iofp + 1) == '\\') {
				path = path.substring(0, iofp) + path.substring(iofp + 1);
				currEvalStart = iofp + 1;
			} else if (path.charAt(iofp + 1) != '&') {
				throw new Exception(String.format("Unknown escaped char: %c",
						path.charAt(iofp + 1)));
			} else {
				tokens.add(path.substring(0, iofp));
				path = path.substring(iofp + 2);
				currEvalStart = 0;
			}
		}

		if (tokens.size() == 0)
			throw new Exception("No tokens given!");
		ClassDescription classdef = Factory.ClassDescription.fetchInstance(os,
				className, null);

		List<PropertyEvaluator> ret = new ArrayList<PropertyEvaluator>();
		for (int cnt = 0; cnt < tokens.size(); cnt++) {
			String token = tokens.get(cnt);
			if (token.length() == 0)
				throw new Exception(String.format("Token %d is empty!", cnt));

			if (token.charAt(0) != '$')
				ret.add(new Constant(token));
			else {
				token = token.substring(1);

				int iofc = token.indexOf(',');
				String prop = null;
				if (iofc < 1
						|| (prop = token.substring(0, iofc).trim()).length() == 0)
					throw new Exception(String.format(
							"No property given in Regexp token %d", cnt));

				boolean cl;
				if ((cl = prop.startsWith("[") && prop.endsWith("]"))
						&& (prop = prop.substring(1, prop.length() - 1).trim())
								.length() == 0)
					throw new Exception(String.format(
							"No property given in Regexp token %d", cnt));

				PropertyDescription theProp = null;
				for (Object pdo : classdef.get_PropertyDescriptions()) {
					PropertyDescription pd = (PropertyDescription) pdo;

					if (prop.equals(pd.get_SymbolicName())) {
						theProp = pd;
						break;
					}
				}

				if (theProp == null)
					throw new Exception(String.format(
							"No property found (%s, Regexp token %d)", prop,
							cnt));

				token = token.substring(iofc + 1);

				iofc = token.indexOf(',');
				int ordinal = -1;

				if (iofc < 1)
					throw new Exception(String.format(
							"No ordinal was found for token %d!", cnt));
				try {
					ordinal = Integer.parseInt(token.substring(0, iofc));
				} catch (Exception e) {
				}
				if (ordinal < 0)
					throw new Exception(String.format(
							"No ordinal was found for token %d!", cnt));

				String regexp = token.substring(iofc + 1).trim();
				if (regexp.length() == 0)
					throw new Exception(String.format(
							"Regexp is empty for token %d!", cnt));

				PropertyRegexp propertyRegexp;
				try {
					propertyRegexp = new PropertyRegexp(
							prop,
							cl && theProp.get_ChoiceList() != null ? getAllChoices(
									theProp.get_ChoiceList().get_ChoiceValues(),
									locale)
									: null, regexp, ordinal);
				} catch (Exception e) {
					throw new Exception(
							String.format(
									"Could not create regex pattern for token %d!",
									cnt), e);
				}

				ret.add(propertyRegexp);
			}
		}

		return ret;
	}

	private static final Map<String, String> getAllChoices(ChoiceList values,
			String locale) {
		Map<String, String> ret = new HashMap<String, String>();
		for (int i = 0; i < values.size(); i++) {
			Choice value = (Choice) values.get(i);
			ChoiceType type = value.get_ChoiceType();
			if (type == ChoiceType.INTEGER || type == ChoiceType.STRING) {
				LocalizedStringList displayNames = value.get_DisplayNames();
				String displayName = null;
				if(locale!=null) for (int j = 0; j < displayNames.size(); j++) {
					LocalizedString ls = (LocalizedString) displayNames.get(j);
					if (locale.equals(ls.get_LocaleName())) {
						displayName = ls.get_LocalizedText();
						break;
					}
				}
				if (displayName == null)
					displayName = value.get_DisplayName();

				if (type == ChoiceType.STRING)
					ret.put(value.get_ChoiceStringValue(), displayName);
				else
					ret.put(value.get_ChoiceIntegerValue().toString(),
							displayName);
			} else
				ret.putAll(getAllChoices(value.get_ChoiceValues(), locale));
		}

		return ret;
	}

	private static Map<String, ClassConfig> getClassRules(
			ObjectStore os) throws Exception {
		os.refresh(new String[]{"SymbolicName"});
		String osid = os.get_SymbolicName();

		if (!classRules.containsKey(osid)) {
			String locale=null;
			Map<Integer,SimpleEntry<String,String>> rules=new HashMap<Integer, SimpleEntry<String,String>>();
			Map<String,List<String>> nullables=new HashMap<String, List<String>>();
			
			FileInputStream fis = null;
			BufferedReader r = null;
			Exception exc = null;
			int linecnt = 0;
			try {

				fis = new FileInputStream(
						System.getProperty(COM_INTERVALLE_FILENET_SUBS_FOLDERINGRULES));
				r = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
				String l;
				
				while ((l = r.readLine()) != null) {
					++linecnt;
					if ((l = l.trim()).length() == 0)
						continue;
					
					if(!l.startsWith(osid+".")) continue;
					l=l.substring(osid.length()+1);

					int iofe = l.indexOf('=');
					if (iofe < 0)
						throw new Exception("No = sign was found in line!");

					String key = l.substring(0, iofe).trim();
					if (key.length() == 0)
						throw new Exception("Key cannot be empty!");
					
					String value = l.substring(iofe + 1).trim();
					if (value.length() == 0)
						throw new Exception(
								"Value cannot be empty!");
					
					
					if (key.startsWith(RULE)) {
						String className=key.substring(RULE.length());						
						if (className.length() == 0)
							throw new Exception("Class name cannot be empty!");

						rules.put(Integer.valueOf(linecnt),new SimpleEntry<String, String>(className,value));
					} else if(key.startsWith(NULLABLE)) {
						String className=key.substring(NULLABLE.length());						
						if (className.length() == 0)
							throw new Exception("Class name cannot be empty!");
						
						if(!nullables.containsKey(className)) nullables.put(className, new ArrayList<String>());
						nullables.get(className).add(value);
						
						
					} else if(CHOICE_LOCALE.equals(key)) {
						locale=value;
					}
				}
			} catch (Exception e) {
				exc = e;
			} finally {
				if (r != null)
					try {
						r.close();
					} catch (Exception e) {
					}
				else if (fis != null)
					try {
						fis.close();
					} catch (Exception e) {
					}
			}

			if (exc != null) {
				String errmsg = String
						.format("Could not load foldering rules from file pointed to by system property %s",
								COM_INTERVALLE_FILENET_SUBS_FOLDERINGRULES);
				if (linecnt > 0)
					errmsg += String.format(". Line number: %d", linecnt);

				throw new Exception(errmsg, exc);
			}

			Map<String, ClassConfig> newClassRules = new LinkedHashMap<String, ClassConfig>();


			for(Integer i:rules.keySet()) {
				SimpleEntry<String, String> classRule = rules.get(i);
				String className=classRule.getKey();
				String value=classRule.getValue();
				
				List<PropertyEvaluator> parsePathExpression;
				try {
					parsePathExpression = parsePathExpression(
							os, className, value,locale);
				} catch(Exception e) {
					String errmsg = String
							.format("Could not load foldering rules from file pointed to by system property %s. Line number: %d",
									COM_INTERVALLE_FILENET_SUBS_FOLDERINGRULES,i);

					throw new Exception(errmsg, e);
					
				}

				if (!newClassRules.containsKey(className))
					newClassRules.put(className,
							new ClassConfig());
				
				newClassRules.get(className).evaluators.add(parsePathExpression);

			}
			
			for(String className:nullables.keySet()) {
				if (!newClassRules.containsKey(className))
					newClassRules.put(className,
							new ClassConfig());

				newClassRules.get(className).nullableProps=nullables.get(className);
			}
			
			classRules.put(osid, newClassRules);
		}

		return classRules.get(osid);
	}	

	private static String replaceIllegalChars(String str) {
		return str.replaceAll("\\\\","_").replaceAll("/", "_").replaceAll(":", "_").replaceAll("\\*", "_").replaceAll("\\?", "_").replaceAll("\"", "_").replaceAll("<", "_").replaceAll(">", "_").replaceAll("\\|", "_");
	}

}
