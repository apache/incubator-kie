/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.base.definitions.rule.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.base.EnabledBoolean;
import org.drools.base.base.SalienceInteger;
import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.rule.ConsequenceMetaData;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Dialectable;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElementFactory;
import org.drools.base.rule.InvalidPatternException;
import org.drools.base.rule.InvalidRuleException;
import org.drools.base.rule.LogicTransformer;
import org.drools.base.rule.RuleConditionElement;
import org.drools.base.rule.accessor.CompiledInvoker;
import org.drools.base.rule.accessor.Enabled;
import org.drools.base.rule.accessor.Salience;
import org.drools.base.rule.accessor.Wireable;
import org.drools.base.rule.consequence.Consequence;
import org.drools.base.time.impl.Timer;
import org.drools.util.StringUtils;
import org.kie.api.definition.rule.Query;
import org.kie.api.io.Resource;
import org.kie.api.runtime.rule.AgendaGroup;
import org.kie.internal.definition.rule.InternalRule;

public class RuleImpl implements Externalizable,
        Wireable,
        Dialectable,
                                 InternalRule,
                                 Query {

    private static final int NO_LOOP_BIT = 1;
    private static final int AUTO_FOCUS_BIT =           1 << 1;
    private static final int LOCK_ON_ACTIVE_BIT =       1 << 2;
    private static final int LOGICAL_DEPENDENCY_BIT =   1 << 3;
    private static final int SEMANTICALLY_VALID_BIT =   1 << 4;
    private static final int EAGER_BIT =                1 << 5;
    private static final int DATA_DRIVEN_BIT =          1 << 6;
    private static final int ALL_MATCHES_BIT =          1 << 7;

    public static final String DEFAULT_CONSEQUENCE_NAME = "default";

    /** The parent pkg */
    private String                   pkg;

    /** Name of the rule. */
    private String                   name;

    /** Parent Rule Name, optional */
    private RuleImpl                 parent;

    private List<RuleImpl>           children;

    /** Salience value. */
    private Salience salience = SalienceInteger.DEFAULT_SALIENCE;

    /** The Rule is dirty after patterns have been added */
    private boolean                  dirty;
    private Map<String, Declaration> declarations;
    private Map<String, String[]>    requiredDeclarations = new HashMap<>();

    private GroupElement lhsRoot;

    private String                   dialect;

    private String                   agendaGroup = AgendaGroup.MAIN;

    private Map<String, Object>      metaAttributes = new HashMap<>();

    /** Consequence. */
    private Consequence consequence;

    private Map<String, Consequence> namedConsequences;

    /** Timer semantics that controls the firing of a rule */
    private Timer timer;

    /** Load order in Package */
    private int                      loadOrder;

    private String                   activationGroup;

    private String                   ruleFlowGroup;

    private String[]                 calendars;

    private Calendar                 dateEffective;

    private Calendar                 dateExpires;

    private Enabled enabled = EnabledBoolean.ENABLED_TRUE;

    private Resource                 resource;

    protected String                 activationListener;

    private ConsequenceMetaData consequenceMetaData = new ConsequenceMetaData();

    private List<QueryImpl>          usedQueries;

    private Collection<QueryImpl>    dependingQueries;

    private int                      ruleFlags;

    private String                   ruleUnitClassName;

    public RuleImpl() { }

    /**
     * Construct a
     * <code>Rule<code> with the given name for the specified pkg parent
     *
     * @param name
     *            The name of this rule.
     */
    public RuleImpl(String name) {
        this.name = name;
        this.lhsRoot = GroupElementFactory.newAndInstance();
        setSemanticallyValid(true);
        setActivationListener( "agenda" );
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( pkg );
        out.writeObject( name );
        out.writeObject( parent );
        out.writeObject( salience );
        out.writeBoolean( dirty );
        out.writeObject( declarations );
        out.writeObject( lhsRoot );
        out.writeObject( dialect );
        out.writeObject( agendaGroup );
        out.writeObject( metaAttributes );
        out.writeObject( requiredDeclarations );

        if ( CompiledInvoker.isCompiledInvoker( this.consequence ) ) {
            out.writeObject( null );
            out.writeObject( null );
        } else {
            out.writeObject( this.consequence );
            out.writeObject( this.namedConsequences);
        }
        out.writeObject(timer);
        out.writeInt(loadOrder);
        out.writeObject(activationGroup);
        out.writeObject( ruleFlowGroup );
        out.writeObject( calendars );
        out.writeObject( dateEffective );
        out.writeObject( dateExpires );
        out.writeObject( enabled );
        out.writeObject( resource );
        out.writeObject( activationListener );
        out.writeObject(consequenceMetaData);
        out.writeObject( usedQueries );
        out.writeInt(ruleFlags);

        out.writeObject( ruleUnitClassName );
    }

    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {
        pkg = (String) in.readObject();
        name = (String) in.readObject();
        parent = (RuleImpl) in.readObject();
        salience = (Salience) in.readObject();

        dirty = in.readBoolean();
        declarations = (Map<String, Declaration>) in.readObject();
        lhsRoot = (GroupElement) in.readObject();
        dialect = (String) in.readObject();
        agendaGroup = (String) in.readObject();
        metaAttributes = (Map<String, Object>) in.readObject();
        requiredDeclarations = (Map<String, String[]>) in.readObject();

        consequence = (Consequence) in.readObject();
        namedConsequences = (Map<String, Consequence>) in.readObject();
        timer = (Timer) in.readObject();
        loadOrder = in.readInt();
        activationGroup = (String) in.readObject();
        ruleFlowGroup = (String) in.readObject();
        calendars =(String[]) in.readObject();
        dateEffective = (Calendar) in.readObject();
        dateExpires = (Calendar) in.readObject();
        enabled = (Enabled) in.readObject();
        resource = (Resource) in.readObject();
        activationListener = ( String ) in.readObject();
        consequenceMetaData = ( ConsequenceMetaData ) in.readObject();
        usedQueries = (List<QueryImpl>) in.readObject();
        ruleFlags = in.readInt();

        ruleUnitClassName = (String) in.readObject();
    }

    public void addUsedQuery(QueryImpl query) {
        if (usedQueries == null) {
            usedQueries = new ArrayList<>();
        }
        usedQueries.add(query);
    }

    /**
     * Returns the lists of queries from which this rule (or query) depends on ordered
     * by their relative dependencies, e.g. if R1 -> A -> B -> C (where the letter are queries)
     * it will return [C, B, A]
     */
    public Collection<QueryImpl> getDependingQueries() {
        if (dependingQueries == null) {
            dependingQueries = usedQueries == null ? Collections.emptyList() : collectDependingQueries(new ArrayDeque<>());
        }
        return dependingQueries;
    }

    protected Collection<QueryImpl> collectDependingQueries(Deque<QueryImpl> accumulator) {
        if (usedQueries == null) {
            return accumulator;
        }
        for (QueryImpl query : usedQueries) {
            if (!accumulator.contains(query)) {
                accumulator.offerFirst(query);
                query.collectDependingQueries(accumulator);
            }
        }
        return accumulator;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    /**
     * Returns the Timer semantics for a rule. Timer based rules are not added directly to the Agenda
     * instead they are scheduled for Agenda addition, based on the timer.
     */
    public Timer getTimer() {
        return timer;
    }

    /**
     * Sets the timer semantics for a rule. Timer based rules are not added directly to the Agenda
     * instead they are scheduled for Agenda addition, based on the timer.
     */
    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    /**
     * Determine if this rule is internally consistent and valid.
     * This will include checks to make sure the rules semantic components (actions and predicates)
     * are valid.
     *
     * No exception is thrown.
     * <p>
     * A <code>Rule</code> must include at least one parameter declaration and
     * one condition.
     * </p>
     *
     * @return <code>true</code> if this rule is valid, else
     *         <code>false</code>.
     */
    public boolean isValid() {
        //if ( this.patterns.size() == 0 ) {
        //    return false;
        //}

        return !(this.consequence == null || !isSemanticallyValid());
    }

    public String getPackage() {
        return this.pkg;
    }

    public RuleImpl setPackage( String pkg ) {
        this.pkg = pkg;
        return this;
    }

    public String getPackageName() {
        return this.pkg;
    }

    /**
     * Retrieve the name of this rule.
     *
     * @return The name of this rule.
     */
    public String getName() {
        return this.name;
    }

    @Override
    public String getFullyQualifiedName() {
        return getPackageName() + "." + getName();
    }

    /**
     * Retrieve the <code>Rule</code> salience.
     *
     * @return The salience.
     */
    public Salience getSalience() {
        return this.salience;
    }

    /**
     * Retrieve the <code>Rule</code> salience value.
     *
     * @return The salience value.
     */
    public int getSalienceValue() {
        return getSalience().getValue();
    }

    /**
     * Returns <code>true</code> if the rule uses dynamic salience, <code>false</code> otherwise.
     *
     * @return <code>true</code> if the rule uses dynamic salience, else <code>false</code>.
     */
    public boolean isSalienceDynamic() {
        return getSalience().isDynamic();
    }

    /**
     * Set the <code>Rule<code> salience.
     *
     *  @param salience The salience.
     */
    public void setSalience(final Salience salience) {
        this.salience = salience;
        if ( salience.isDynamic() ) {
            setEager(true);
        }
    }

    public String getAgendaGroup() {
        return agendaGroup;
    }

    public RuleImpl setAgendaGroup(final String agendaGroup) {
        this.agendaGroup = agendaGroup;
        return this;
    }

    @Override
    public boolean isMainAgendaGroup() {
        return AgendaGroup.MAIN.equals( agendaGroup );
    }

    private void set( int flag, boolean b ) {
        if (b) {
            ruleFlags |= flag;
        } else {
            ruleFlags &= (0xffffffff - flag);
        }
    }

    private boolean isSet(int flag) {
        return (ruleFlags & flag) == flag;
    }

    public boolean isNoLoop() {
        return isSet(NO_LOOP_BIT);
    }

    /**
     * This returns true is the rule is effective.
     * If the rule is not effective, it cannot activate.
     *
     * This uses the dateEffective, dateExpires and enabled flag to decide this.
     */
    public boolean isEffective(BaseTuple tuple,
                               Declaration[] declarations,
                               ValueResolver valueResolver) {
        if ( !this.enabled.getValue( tuple,
                                     declarations,
                                     this,
                                     valueResolver ) ) {
            return false;
        }
        if ( this.dateEffective == null && this.dateExpires == null ) {
            return true;
        } else {
            Calendar now = Calendar.getInstance();
            now.setTimeInMillis( valueResolver.getCurrentTime() );

            if ( this.dateEffective != null && this.dateExpires != null ) {
                return (now.after( this.dateEffective ) && now.before( this.dateExpires ));
            } else if ( this.dateEffective != null ) {
                return (now.after( this.dateEffective ));
            } else {
                return (now.before( this.dateExpires ));
            }
        }
    }

    public void setNoLoop(final boolean noLoop) {
        set(NO_LOOP_BIT, noLoop);
    }

    public boolean getAutoFocus() {
        return isSet(AUTO_FOCUS_BIT);
    }

    public void setAutoFocus(final boolean autoFocus) {
        set(AUTO_FOCUS_BIT, autoFocus);
        setEager(autoFocus);
    }

    public String getActivationGroup() {
        return this.activationGroup;
    }

    public void setActivationGroup(final String activationGroup) {
        this.activationGroup = activationGroup;
        setEager(!StringUtils.isEmpty(activationGroup));
    }

    public String getRuleFlowGroup() {
        return this.ruleFlowGroup;
    }

    public void setRuleFlowGroup(final String ruleFlowGroup) {
        this.ruleFlowGroup = ruleFlowGroup;
    }

    /**
     * Retrieve a parameter <code>Declaration</code> by identifier.
     *
     * @param identifier
     *            The identifier.
     *
     * @return The declaration or <code>null</code> if no declaration matches
     *         the <code>identifier</code>.
     */
    public Declaration getDeclaration(final String identifier) {
        if ( this.dirty || (this.declarations == null) ) {
            this.declarations = this.getExtendedLhs( this, null ).getOuterDeclarations();
            this.dirty = false;
        }
        return this.declarations.get( identifier );
    }

    public String[] getRequiredDeclarationsForConsequence(String consequenceName) {
        String[] declarations = requiredDeclarations.get(consequenceName);
        return declarations != null ? declarations : new String[0];
    }

    public void setRequiredDeclarationsForConsequence(String consequenceName, String[] requiredDeclarations) {
        this.requiredDeclarations.put(consequenceName, requiredDeclarations);
    }

    /**
     * This field is updated at runtime, when the first logical assertion is done. I'm currently not too happy about having this determine at runtime
     * but its currently easier than trying to do this at compile time, although eventually this should be changed
     * @return
     */
    public boolean hasLogicalDependency() {
        return isSet(LOGICAL_DEPENDENCY_BIT);
    }

    public void setHasLogicalDependency(boolean hasLogicalDependency) {
        set(LOGICAL_DEPENDENCY_BIT, hasLogicalDependency);
    }

    public boolean isLockOnActive() {
        return isSet(LOCK_ON_ACTIVE_BIT);
    }

    public void setLockOnActive(final boolean lockOnActive) {
        set(LOCK_ON_ACTIVE_BIT, lockOnActive);
    }

    /**
     * Retrieve the set of all <i>root fact object </i> parameter
     * <code>Declarations</code>.
     *
     * @return The Set of <code>Declarations</code> in order which specify the
     *         <i>root fact objects</i>.
     */
    public Map<String, Declaration> getDeclarations() {
        if ( this.dirty || (this.declarations == null) ) {
            this.declarations = this.getExtendedLhs( this, null ).getOuterDeclarations();
            this.dirty = false;
        }
        return this.declarations;
    }

    /**
     * Add a pattern to the rule. All patterns are searched for bindings which are then added to the rule
     * as declarations
     *
     * @param element
     *            The <code>Test</code> to add.
     * @throws InvalidRuleException
     */
    public void addPattern(final RuleConditionElement element) {
        this.dirty = true;
        this.lhsRoot.addChild( element );
    }

    public void addSequence(final Sequence element) {
        this.dirty = true;
        this.lhsRoot.addChild( element );
    }

    /**
     * Retrieve the <code>List</code> of <code>Conditions</code> for this
     * rule.
     *
     * @return The <code>List</code> of <code>Conditions</code>.
     */
    public GroupElement getLhs() {
        return this.lhsRoot;
    }

    public void setLhs(final GroupElement lhsRoot) {
        this.dirty = true;
        this.lhsRoot = lhsRoot;
    }

    private GroupElement getExtendedLhs(RuleImpl rule,
                                        GroupElement fromChild) {
        //combine rules LHS with Parent "Extends"
        final GroupElement lhs = rule.lhsRoot.cloneOnlyGroup();
        //use the children passed from prior child rules, and combine with current LHS (at the end)
        if ( null != fromChild ) {
            //Have GroupElement from a child rule, so combine it
            lhs.getChildren().addAll( fromChild.getChildren() );
        }
        //move recursively up the tree
        if ( rule.parent != null ) {
            return getExtendedLhs( rule.parent,
                                   lhs );
        }
        //at the top of the tree, return combined LHS
        //TODO Merge LHS for performace

        return lhs;
    }

    /**
     * Uses the LogicTransformer to process the Rule patters - if no ORs are
     * used this will return an array of a single AND element. If there are Ors
     * it will return an And element for each possible logic branch. The
     * processing uses as a clone of the Rule's patterns, so they are not
     * changed.
     *
     * @return
     * @throws InvalidPatternException
     */
    public GroupElement[] getTransformedLhs(LogicTransformer transformer, Map<String, Type> globals ) throws InvalidPatternException {
        //Moved to getExtendedLhs --final GroupElement cloned = (GroupElement) this.lhsRoot.clone();
        return transformer.transform( getExtendedLhs( this, null ), globals );
    }

    public void wire(Object object) {
        if ( object instanceof Consequence c ) {
            if ( DEFAULT_CONSEQUENCE_NAME.equals( c.getName() ) ) {
                setConsequence( c );
            } else {
                addNamedConsequence(c.getName(), c);
            }
        } else if ( object instanceof Salience s ) {
            setSalience( s );
        } else if ( object instanceof Enabled e ) {
            setEnabled( e );
        }
    }

    /**
     * Set the <code>Consequence</code> that is associated with the successful
     * match of this rule.
     *
     * @param consequence
     *            The <code>Consequence</code> to attach to this
     *            <code>Rule</code>.
     */
    public void setConsequence(final Consequence consequence) {
        this.consequence = consequence;
    }

    /**
     * Retrieve the <code>Consequence</code> associated with this
     * <code>Rule</code>.
     *
     * @return The <code>Consequence</code>.
     */
    public Consequence getConsequence() {
        return this.consequence;
    }

    public boolean hasNamedConsequences() {
        return namedConsequences != null && !namedConsequences.isEmpty();
    }

    public Consequence getNamedConsequence(String consequenceName)  {
        Consequence consequence = namedConsequences != null ? namedConsequences.get(consequenceName) : null;
        return consequence == null && parent != null ? parent.getNamedConsequence(consequenceName) : consequence;
    }

    public void addNamedConsequence(String name, Consequence consequence) {
        if ( this.namedConsequences == null ) {
            this.namedConsequences = new HashMap<>();
        }
        this.namedConsequences.put(name, consequence);
    }

    public int getLoadOrder() {
        return this.loadOrder;
    }

    public void setLoadOrder(final int loadOrder) {
        this.loadOrder = loadOrder;
    }

    public boolean isEager() {
        return isSet(EAGER_BIT);
    }

    public void setEager(boolean eager) {
        set(EAGER_BIT, eager);
    }

    public boolean isDataDriven() {
        return isSet(DATA_DRIVEN_BIT);
    }

    public void setDataDriven(boolean dataDriven) {
        set(DATA_DRIVEN_BIT, dataDriven);
    }

    public boolean isAllMatches() {
        return isSet(ALL_MATCHES_BIT);
    }

    public void setAllMatches(boolean allMatches) {
        set(ALL_MATCHES_BIT, allMatches);
    }

    public String toString() {
        return "[Rule name=" + this.name + ", agendaGroup=" + this.agendaGroup + ", salience=" + this.salience + ", no-loop=" + isNoLoop() + "]";
    }

    public String toRuleNameAndPathString() {
        String path = getResource() != null ? getResource().getSourcePath() : null;
        return "Rule \"" + getName() + "\"" + (path != null ? " in " + path : "");
    }

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((name == null) ? 0 : name.hashCode());
        result = PRIME * result + ((pkg == null) ? 0 : pkg.hashCode());
        return result;
    }

    public boolean equals(Object obj) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null || getClass() != obj.getClass() ) {
            return false;
        }
        final RuleImpl other = (RuleImpl) obj;
        if ( name == null ) {
            if ( other.name != null ) {
            return false;
            }
        } else if ( !name.equals( other.name ) ) {
            return false;
        }
        if ( pkg == null ) {
            return other.pkg == null;
        } else{
            return pkg.equals(other.pkg);
        }
    }

    public void setSemanticallyValid(final boolean valid) {
        set(SEMANTICALLY_VALID_BIT, valid);
    }

    /**
     * This will return if the semantic actions or predicates in the rules
     * are valid.
     * This is provided so that lists of rules can be provided even if their semantic actions
     * do not "compile" etc.
     */
    public boolean isSemanticallyValid() {
        return isSet(SEMANTICALLY_VALID_BIT);
    }

    public boolean hasCalendars() {
        return calendars != null && calendars.length > 0;
    }

    public String[] getCalendars() {
        return calendars;
    }

    public void setCalendars(String[] calendars) {
        this.calendars = calendars;
    }

    /**
     * Sets the date from which this rule takes effect (can include time to the millisecond).
     * @param effectiveDate
     */
    public void setDateEffective(final Calendar effectiveDate) {
        this.dateEffective = effectiveDate;
    }

    /**
     * Sets the date after which the rule will no longer apply (can include time to the millisecond).
     * @param expiresDate
     */
    public void setDateExpires(final Calendar expiresDate) {
        this.dateExpires = expiresDate;
    }

    public Calendar getDateEffective() {
        return this.dateEffective;
    }

    public Calendar getDateExpires() {
        return this.dateExpires;
    }

    /**
     * A rule is enabled by default. This can explicitly disable it in which case it will never activate.
     */
    public void setEnabled(final Enabled b) {
        this.enabled = b;
    }

    public Enabled getEnabled() {
        return enabled;
    }


    public boolean isEnabled(BaseTuple tuple,
                             Declaration[] enabledDeclarations,
                             ValueResolver valueResolver) {
        return this.enabled.getValue( tuple, enabledDeclarations, this, valueResolver );
    }

    public void addMetaAttribute(String key,
                                 Object value) {
        this.metaAttributes.put( key,
                                 value );
    }

    public String getActivationListener() {
        return activationListener;
    }

    public void setActivationListener(String activationListener) {
        this.activationListener = activationListener;
    }

    public Map<String, Object> getMetaData() {
        return Collections.unmodifiableMap(metaAttributes);
    }

    public Object getMetaData(String name) {
        return metaAttributes.get(name);
    }

    public void setParent(RuleImpl parent) {
        this.parent = parent;
        parent.addChild( this );
    }

    public RuleImpl getParent() {
        return parent;
    }

    public synchronized void addChild(RuleImpl child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    public void removeChild(RuleImpl child) {
        children.remove( child );
    }

    public List<RuleImpl> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public boolean isQuery() {
        return false;
    }

    public KnowledgeType getKnowledgeType() {
        return KnowledgeType.RULE;
    }

    public String getNamespace() {
        return getPackage();
    }

    public String getId() {
        return getName();
    }

    public ConsequenceMetaData getConsequenceMetaData() {
        return consequenceMetaData;
    }

    public String getRuleUnitClassName() {
        return ruleUnitClassName;
    }

    public void setRuleUnitClass( Class<?> ruleUnit ) {
        setRuleUnitClassName( ruleUnit.getName() );
    }

    public void setRuleUnitClassName( String ruleUnitClassName ) {
        this.ruleUnitClassName = ruleUnitClassName;
    }

    public Declaration[] findEnabledDeclarations(Map<String, Declaration> decls) {
        return this.enabled.findDeclarations(decls);
    }

    public Declaration[] findSalienceDeclarations(Map<String, Declaration> decls) {
        return this.salience.findDeclarations(decls);
    }
}
