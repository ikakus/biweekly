package biweekly.property.marshaller;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;

import biweekly.ICalDataType;
import biweekly.io.json.JCalValue;
import biweekly.io.xml.XCalElement;
import biweekly.io.xml.XCalNamespaceContext;
import biweekly.parameter.ICalParameters;
import biweekly.property.RecurrenceProperty;
import biweekly.util.ICalDateFormatter;
import biweekly.util.ListMultimap;
import biweekly.util.Recurrence;
import biweekly.util.Recurrence.DayOfWeek;
import biweekly.util.Recurrence.Frequency;
import biweekly.util.StringUtils;
import biweekly.util.StringUtils.JoinMapCallback;
import biweekly.util.XmlUtils;

/*
 Copyright (c) 2013, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Marshals properties whose values are {@link Recurrence}.
 * @param <T> the property class
 * @author Michael Angstadt
 */
public abstract class RecurrencePropertyMarshaller<T extends RecurrenceProperty> extends ICalPropertyMarshaller<T> {
	public RecurrencePropertyMarshaller(Class<T> clazz, String propertyName) {
		super(clazz, propertyName, ICalDataType.RECUR);
	}

	@Override
	protected String _writeText(T property) {
		Recurrence recur = property.getValue();
		if (recur == null) {
			return "";
		}

		ListMultimap<String, Object> components = buildComponents(recur, false);
		return StringUtils.join(components.getMap(), ";", new JoinMapCallback<String, List<Object>>() {
			public void handle(StringBuilder sb, String key, List<Object> values) {
				sb.append(key).append('=');
				StringUtils.join(values, ",", sb);
			}
		});
	}

	@Override
	protected T _parseText(String value, ICalDataType dataType, ICalParameters parameters, List<String> warnings) {
		ListMultimap<String, String> rules = new ListMultimap<String, String>();
		for (String component : value.split(";")) {
			String split[] = component.split("=");
			if (split.length < 2) {
				warnings.add("Skipping invalid recurrence rule component: " + component);
				continue;
			}

			String name = split[0].toUpperCase();
			List<String> values = Arrays.asList(split[1].split(","));
			rules.putAll(name, values);
		}

		Recurrence.Builder builder = new Recurrence.Builder((Frequency) null);

		parseFreq(rules, builder, warnings);
		parseUntil(rules, builder, warnings);
		parseCount(rules, builder, warnings);
		parseInterval(rules, builder, warnings);
		parseBySecond(rules, builder, warnings);
		parseByMinute(rules, builder, warnings);
		parseByHour(rules, builder, warnings);
		parseByDay(rules, builder, warnings);
		parseByMonthDay(rules, builder, warnings);
		parseByYearDay(rules, builder, warnings);
		parseByWeekNo(rules, builder, warnings);
		parseByMonth(rules, builder, warnings);
		parseBySetPos(rules, builder, warnings);
		parseWkst(rules, builder, warnings);
		parseXRules(rules, builder, warnings); //must be called last

		return newInstance(builder.build());
	}

	@Override
	protected void _writeXml(T property, XCalElement element) {
		XCalElement recurElement = element.append(dataType(property));

		Recurrence recur = property.getValue();
		if (recur == null) {
			return;
		}

		ListMultimap<String, Object> components = buildComponents(recur, true);
		for (Map.Entry<String, List<Object>> component : components) {
			String name = component.getKey().toLowerCase();
			for (Object value : component.getValue()) {
				recurElement.append(name, value.toString());
			}
		}
	}

	@Override
	protected T _parseXml(XCalElement element, ICalParameters parameters, List<String> warnings) {
		XCalElement value = element.child(defaultDataType);
		if (value == null) {
			throw missingXmlElements(defaultDataType);
		}

		ListMultimap<String, String> rules = new ListMultimap<String, String>();
		for (Element child : XmlUtils.toElementList(value.getElement().getChildNodes())) {
			if (!XCalNamespaceContext.XCAL_NS.equals(child.getNamespaceURI())) {
				continue;
			}

			String name = child.getLocalName().toUpperCase();
			String text = child.getTextContent();
			rules.put(name, text);
		}

		Recurrence.Builder builder = new Recurrence.Builder((Frequency) null);

		parseFreq(rules, builder, warnings);
		parseUntil(rules, builder, warnings);
		parseCount(rules, builder, warnings);
		parseInterval(rules, builder, warnings);
		parseBySecond(rules, builder, warnings);
		parseByMinute(rules, builder, warnings);
		parseByHour(rules, builder, warnings);
		parseByDay(rules, builder, warnings);
		parseByMonthDay(rules, builder, warnings);
		parseByYearDay(rules, builder, warnings);
		parseByWeekNo(rules, builder, warnings);
		parseByMonth(rules, builder, warnings);
		parseBySetPos(rules, builder, warnings);
		parseWkst(rules, builder, warnings);
		parseXRules(rules, builder, warnings); //must be called last

		return newInstance(builder.build());
	}

	@Override
	protected JCalValue _writeJson(T property) {
		Recurrence recur = property.getValue();
		if (recur == null) {
			return JCalValue.object(new ListMultimap<String, Object>(0));
		}

		ListMultimap<String, Object> components = buildComponents(recur, true);

		//lower-case all the keys
		ListMultimap<String, Object> object = new ListMultimap<String, Object>(components.keySet().size());
		for (Map.Entry<String, List<Object>> entry : components) {
			String key = entry.getKey().toLowerCase();
			object.putAll(key, entry.getValue());
		}

		return JCalValue.object(object);
	}

	@Override
	protected T _parseJson(JCalValue value, ICalDataType dataType, ICalParameters parameters, List<String> warnings) {
		Recurrence.Builder builder = new Recurrence.Builder((Frequency) null);

		//upper-case the keys
		ListMultimap<String, String> object = value.asObject();
		ListMultimap<String, String> rules = new ListMultimap<String, String>(object.keySet().size());
		for (Map.Entry<String, List<String>> entry : object) {
			String key = entry.getKey().toUpperCase();
			rules.putAll(key, entry.getValue());
		}

		parseFreq(rules, builder, warnings);
		parseUntil(rules, builder, warnings);
		parseCount(rules, builder, warnings);
		parseInterval(rules, builder, warnings);
		parseBySecond(rules, builder, warnings);
		parseByMinute(rules, builder, warnings);
		parseByHour(rules, builder, warnings);
		parseByDay(rules, builder, warnings);
		parseByMonthDay(rules, builder, warnings);
		parseByYearDay(rules, builder, warnings);
		parseByWeekNo(rules, builder, warnings);
		parseByMonth(rules, builder, warnings);
		parseBySetPos(rules, builder, warnings);
		parseWkst(rules, builder, warnings);
		parseXRules(rules, builder, warnings); //must be called last

		return newInstance(builder.build());
	}

	/**
	 * Creates a new instance of the recurrence property.
	 * @param recur the recurrence value
	 * @return the new instance
	 */
	protected abstract T newInstance(Recurrence recur);

	private void parseFreq(ListMultimap<String, String> rules, Recurrence.Builder builder, List<String> warnings) {
		List<String> values = rules.removeAll("FREQ");
		if (values.isEmpty()) {
			return;
		}

		String value = values.get(0);
		try {
			builder.frequency(Frequency.valueOf(value.toUpperCase()));
		} catch (IllegalArgumentException e) {
			warnings.add("Unable to parse FREQ value: " + value);
		}
	}

	private void parseUntil(ListMultimap<String, String> rules, Recurrence.Builder builder, List<String> warnings) {
		List<String> values = rules.removeAll("UNTIL");
		if (values.isEmpty()) {
			return;
		}

		String value = values.get(0);
		try {
			Date date = date(value).parse();
			boolean hasTime = ICalDateFormatter.dateHasTime(value);
			builder.until(date, hasTime);
		} catch (IllegalArgumentException e) {
			warnings.add("Unable to parse UNTIL value: " + value);
		}
	}

	private void parseCount(ListMultimap<String, String> rules, Recurrence.Builder builder, List<String> warnings) {
		List<String> values = rules.removeAll("COUNT");
		if (values.isEmpty()) {
			return;
		}

		String value = values.get(0);
		try {
			builder.count(Integer.valueOf(value));
		} catch (NumberFormatException e) {
			warnings.add("Unable to parse COUNT value: " + value);
		}
	}

	private void parseInterval(ListMultimap<String, String> rules, Recurrence.Builder builder, List<String> warnings) {
		List<String> values = rules.removeAll("INTERVAL");
		if (values.isEmpty()) {
			return;
		}

		String value = values.get(0);
		try {
			builder.interval(Integer.valueOf(value));
		} catch (NumberFormatException e) {
			warnings.add("Unable to parse INTERVAL value: " + value);
		}
	}

	private void parseBySecond(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYSECOND", rules.removeAll("BYSECOND"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.bySecond(value);
			}
		});
	}

	private void parseByMinute(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYMINUTE", rules.removeAll("BYMINUTE"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.byMinute(value);
			}
		});
	}

	private void parseByHour(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYHOUR", rules.removeAll("BYHOUR"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.byHour(value);
			}
		});
	}

	private void parseByDay(ListMultimap<String, String> rules, Recurrence.Builder builder, List<String> warnings) {
		Pattern p = Pattern.compile("^([-+]?\\d+)?(.*)$");
		for (String value : rules.removeAll("BYDAY")) {
			Matcher m = p.matcher(value);
			if (!m.find()) {
				//this should never happen
				//the regex contains a "match-all" pattern and should never not find anything
				warnings.add("Unable to parse BYDAY value (invalid format): " + value);
				continue;
			}

			String dayStr = m.group(2);
			DayOfWeek day = DayOfWeek.valueOfAbbr(dayStr);
			if (day == null) {
				warnings.add("Unable to parse BYDAY value (invalid day of the week): " + value);
				continue;
			}

			String prefixStr = m.group(1);
			Integer prefix = (prefixStr == null) ? null : Integer.valueOf(prefixStr); //no need to catch NumberFormatException because the regex guarantees that it will be a number

			builder.byDay(prefix, day);
		}
	}

	private void parseByMonthDay(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYMONTHDAY", rules.removeAll("BYMONTHDAY"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.byMonthDay(value);
			}
		});
	}

	private void parseByYearDay(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYYEARDAY", rules.removeAll("BYYEARDAY"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.byYearDay(value);
			}
		});
	}

	private void parseByWeekNo(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYWEEKNO", rules.removeAll("BYWEEKNO"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.byWeekNo(value);
			}
		});
	}

	private void parseByMonth(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYMONTH", rules.removeAll("BYMONTH"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.byMonth(value);
			}
		});
	}

	private void parseBySetPos(ListMultimap<String, String> rules, final Recurrence.Builder builder, List<String> warnings) {
		parseIntegerList("BYSETPOS", rules.removeAll("BYSETPOS"), warnings, new ListHandler() {
			public void handle(Integer value) {
				builder.bySetPos(value);
			}
		});
	}

	private void parseWkst(ListMultimap<String, String> rules, Recurrence.Builder builder, List<String> warnings) {
		List<String> values = rules.removeAll("WKST");
		if (values.isEmpty()) {
			return;
		}

		String value = values.get(0);
		DayOfWeek day = DayOfWeek.valueOfAbbr(value);
		if (day == null) {
			warnings.add("Unable to parse WKST (invalid day of the week): " + value);
			return;
		}

		builder.workweekStarts(day);
	}

	private void parseXRules(ListMultimap<String, String> rules, Recurrence.Builder builder, List<String> warnings) {
		for (Map.Entry<String, List<String>> rule : rules) {
			String name = rule.getKey();
			for (String value : rule.getValue()) {
				builder.xrule(name, value);
			}
		}
	}

	private ListMultimap<String, Object> buildComponents(Recurrence recur, boolean extended) {
		ListMultimap<String, Object> components = new ListMultimap<String, Object>();

		//FREQ must come first
		if (recur.getFrequency() != null) {
			components.put("FREQ", recur.getFrequency().name());
		}

		if (recur.getUntil() != null) {
			String s = date(recur.getUntil()).time(recur.hasTimeUntilDate()).extended(extended).write();
			components.put("UNTIL", s);
		}

		if (recur.getCount() != null) {
			components.put("COUNT", recur.getCount());
		}

		if (recur.getInterval() != null) {
			components.put("INTERVAL", recur.getInterval());
		}

		addIntegerListComponent(components, "BYSECOND", recur.getBySecond());
		addIntegerListComponent(components, "BYMINUTE", recur.getByMinute());
		addIntegerListComponent(components, "BYHOUR", recur.getByHour());

		Iterator<Integer> prefixIt = recur.getByDayPrefixes().iterator();
		Iterator<DayOfWeek> dayIt = recur.getByDay().iterator();
		while (prefixIt.hasNext() && dayIt.hasNext()) {
			Integer prefix = prefixIt.next();
			DayOfWeek day = dayIt.next();

			String value = day.getAbbr();
			if (prefix != null) {
				value = prefix + value;
			}
			components.put("BYDAY", value);
		}

		addIntegerListComponent(components, "BYMONTHDAY", recur.getByMonthDay());
		addIntegerListComponent(components, "BYYEARDAY", recur.getByYearDay());
		addIntegerListComponent(components, "BYWEEKNO", recur.getByWeekNo());
		addIntegerListComponent(components, "BYMONTH", recur.getByMonth());
		addIntegerListComponent(components, "BYSETPOS", recur.getBySetPos());

		if (recur.getWorkweekStarts() != null) {
			components.put("WKST", recur.getWorkweekStarts().getAbbr());
		}

		for (Map.Entry<String, List<String>> entry : recur.getXRules().entrySet()) {
			String name = entry.getKey();
			for (String value : entry.getValue()) {
				components.put(name, value);
			}
		}

		return components;
	}

	private void addIntegerListComponent(ListMultimap<String, Object> components, String name, List<Integer> values) {
		for (Integer value : values) {
			components.put(name, value);
		}
	}

	private void parseIntegerList(String name, List<String> values, List<String> warnings, ListHandler handler) {
		for (String value : values) {
			try {
				handler.handle(Integer.valueOf(value));
			} catch (NumberFormatException e) {
				warnings.add("Ignoring non-numeric value found in " + name + ": " + value);
			}
		}
	}

	private static interface ListHandler {
		void handle(Integer value);
	}
}
