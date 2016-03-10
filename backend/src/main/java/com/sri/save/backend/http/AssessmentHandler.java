/*
 * Copyright 2016 SRI International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sri.save.backend.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sri.ai.lumen.atr.ATRSyntax;
import com.sri.ai.lumen.atr.term.ATRTerm;
import com.sri.ai.lumen.syntax.LumenSyntaxError;
import com.sri.ai.patternmatcher.Match;
import com.sri.ai.patternmatcher.graph.Constraint;
import com.sri.pal.AbstractActionDef;
import com.sri.pal.ActionModelDef;
import com.sri.pal.ActionStreamEvent;
import com.sri.pal.Bridge;
import com.sri.pal.GlobalActionListener;
import com.sri.pal.IdiomDef;
import com.sri.pal.Learner;
import com.sri.pal.PALException;
import com.sri.pal.Struct;
import com.sri.pal.TypeDef;
import com.sri.pal.common.TypeNameFactory;
import com.sri.pal.training.aa.ArgCandidateNode;
import com.sri.pal.training.aa.Assessor;
import com.sri.pal.training.aa.ParamPatternNode;
import com.sri.pal.training.aa.SymbolManager;
import com.sri.pal.training.aa.TaskAssessmentResult;
import com.sri.pal.training.aa.constrainteval.EqualityEvaluator;
import com.sri.pal.training.aa.constrainteval.ValueEvaluator;
import com.sri.pal.training.aa.state.PredicateEvaluator;
import com.sri.pal.training.core.assessment.ArgumentLocation;
import com.sri.pal.training.core.assessment.EqualityIssue;
import com.sri.pal.training.core.assessment.ExtraAtomsIssue;
import com.sri.pal.training.core.assessment.MissingAtomIssue;
import com.sri.pal.training.core.assessment.OrderingIssue;
import com.sri.pal.training.core.assessment.StateIssue;
import com.sri.pal.training.core.assessment.TaskAssessment;
import com.sri.pal.training.core.assessment.ValueIssue;
import com.sri.pal.training.core.exercise.ConstraintArgument;
import com.sri.pal.training.core.exercise.EqualityConstraint;
import com.sri.pal.training.core.exercise.Exercise;
import com.sri.pal.training.core.exercise.Parameter;
import com.sri.pal.training.core.exercise.StateConstraint;
import com.sri.pal.training.core.exercise.Step;
import com.sri.pal.training.core.exercise.Task;
import com.sri.pal.training.core.exercise.TaskSolution;
import com.sri.pal.training.core.exercise.Value;
import com.sri.pal.training.core.exercise.ValueConstraint;
import com.sri.pal.training.core.response.TaskResponse;
import com.sri.pal.training.core.util.ResponseUtil;
import com.sri.pal.training.core.util.ValueUtil;
import com.sri.save.backend.XApiRequestTracker;
import com.sri.tasklearning.ui.core.BackendFacade;
import com.sri.tasklearning.ui.core.CoreUIModelFactory;
import com.sri.tasklearning.ui.core.step.ActionStepModel;
import com.sri.tasklearning.ui.core.step.IdiomStepModel;
import com.sri.tasklearning.ui.core.step.StepModel;
import com.sri.tasklearning.ui.core.term.ParameterModel;
import com.sri.tasklearning.ui.core.term.TermModel;

/**
 * Respond to an HTTP request from the EUI by ending the demonstration,
 * performing assessment, and displaying assessment results as an HTML page.
 */
public class AssessmentHandler
        extends AbstractHandler
        implements ResettableActionListener {
    private static final boolean SHOW_EXTRA_ATOMS_ISSUES = true;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Bridge bridge;
    private final ActionListener actionListener;
    private final Exercise exercise;
    private final XApiRequestTracker reqTracker;

    public AssessmentHandler(Bridge bridge,
                             XApiRequestTracker reqTracker,
                             Exercise exer) {
        this.bridge = bridge;
        this.reqTracker = reqTracker;
        exercise = exer;
        actionListener = new ActionListener();
        bridge.addActionListener(actionListener);
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException,
            ServletException {
        // Build a TaskResponse holding the student's actions.
        Learner learner = bridge.getLearner();
        TaskResponse taskresp = new TaskResponse();
        // response.setSecondsElapsed(10 * 1000);
        ResponseUtil.setDemonstration(taskresp, actionListener.getActions(),
                learner);

        // Extract the task and tasksolution (aka gold standard).
        Task task = exercise.getProblem().getTasks().get(0);
        TaskSolution solution = exercise.getSolution().getTaskSolutions()
                .get(0);

        // Assess the student's performance of the task.
        Assessor assessor = new Assessor(bridge);
        SymbolManager symbols = new SymbolManager(bridge);
        TaskAssessmentResult tar = assessor.assessTask(task, solution,
                taskresp, symbols);
        String message = assess(tar, symbols, solution);
        if (message == null) {
            message = exercise.getSuccessHtml();
        }

        // Now send the response back.
        response.setContentType("text/html");
        baseRequest.setHandled(true);
        response.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = response.getWriter()) {
            out.println(message);
        }

        // Notify PERLS, if it sent the original request.
        String refererStr = request.getHeader("Referer");
        if (refererStr.length() == 0) {
            log.info("Empty Referer, so not reporting to PERLS.");
        } else {
            URL referer = new URL(refererStr);
            TaskAssessment ta = tar.assessment();
            reqTracker.exerciseFinished(referer, !ta.hasProblems());
        }
    }

    // TODO This is copy-pasted from TextualFeedbackPane.generateFeedback:
    private String assess(TaskAssessmentResult tar,
                          SymbolManager symbols,
                          TaskSolution taskSolution) {
        BackendFacade.getInstance().connect("assessment");
        TaskAssessment ta = tar.assessment();
        List<MissingAtomIssue> missing_atom_issues = ta.getMissingAtomIssues();
        List<ValueIssue> value_issues = ta.getValueIssues();
        List<EqualityIssue> equality_issues = ta.getEqualityIssues();
        List<OrderingIssue> order_issues = ta.getOrderingIssues();
        List<StateIssue> state_issues = ta.getStateIssues();
        List<ExtraAtomsIssue> extra_atom_issues = ta.getExtraAtomsIssues();
        List<ActionStreamEvent> actions = actionListener.getActions();

        List<ActionStepModel> steps = new ArrayList<ActionStepModel>();
        List<Integer> indexMap = ta.getResponseIndexes().getIndexes();
        for (int idx : indexMap) {
            ActionStreamEvent ase = actions.get(idx);
            ActionStepModel step = new ActionStepModel(ase);
            steps.add(step);
        }

        VelocityEngine ve = new VelocityEngine();
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        ve.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
        ve.init();

        // I hate Java resource loading
        Template tmpl = ve.getTemplate("/assessment.vtl");

        VelocityContext context = new VelocityContext();

        boolean process = false;

        // Do response first
        /*
        List<String> response_atom_msgs = new ArrayList<String>();
        List<ATRDemonstratedAction> actions = response.getDemonstration().getActions();
        for (ATRDemonstratedAction action : actions) {
        	response_atom_msgs.add(action.toString());
        }
        context.put("response_atom_msgs", response_atom_msgs);
        */
        // Do missing steps section first
        List<String> missing_atom_msgs = new ArrayList<String>();
        if (missing_atom_issues != null) {
            for (MissingAtomIssue iss : missing_atom_issues) {
                StepModel sm = createMissingAtomStepModel(symbols,
                        taskSolution, iss, tar);
                AbstractActionDef def = (sm instanceof ActionStepModel) ? ((ActionStepModel) sm)
                        .getActionDefinition() : ((IdiomStepModel) sm)
                        .getActionDefinition();
                List<Object> fancyName;

                if (def.getMetadata("shortFancyName") != null)
                    fancyName = sm.fancyNameFromActionDefinition(def,
                            "shortFancyName");
                else
                    fancyName = sm.getFancyName();

                String s = processFancyName(fancyName);
                missing_atom_msgs.add(0, s);
                process = true;
            }
        }

        context.put("missing_atom_msgs", missing_atom_msgs);

        // Followed by value/equality problems
        List<String> value_equality_trace_msgs = new ArrayList<String>();
        List<List<String>> value_equality_error_msgs = new ArrayList<List<String>>();
        Map<ActionStepModel, Integer> stepMap = new HashMap<ActionStepModel, Integer>();

        List<Object> merged_issues = new ArrayList<Object>();

        if (value_issues != null)
            merged_issues.addAll(value_issues);
        if (equality_issues != null)
            merged_issues.addAll(equality_issues);

        // order the trace messages chronologically
        for (Object iss : merged_issues) {
            ArgumentLocation loc = null;

            if (iss instanceof ValueIssue)
                loc = ((ValueIssue) iss).getLocation();
            else
                loc = ((EqualityIssue) iss).getLocation2();

            ActionStepModel asm = steps.get(loc.getAtomIndex());

            if (!stepMap.containsKey(asm)) {
                String trace = processFancyName(asm.getFancyName());
                value_equality_trace_msgs.add(trace);
                stepMap.put(asm, value_equality_trace_msgs.size() - 1);
            }

            int index = stepMap.get(asm);

            String neg_str = "";
            String val_str = "";
            String rsn_str = "";
            
            List<String> vals = new ArrayList<String>();
            if (iss instanceof ValueIssue) {
                ValueConstraint constraint = ((ValueIssue) iss).getConstraint();
                for (Value val : constraint.getValues()) {
                    Object obj = ValueUtil.getObject(val, bridge);

                    vals.add(obj.toString());
                }

                for (String name : constraint.getRefs()) {
                    Value value = symbols.lookupReference(name);

                    if (value != null) {
                        Object obj = ValueUtil.getObject(value, bridge);

                        vals.add(obj.toString());
                    }
                }

                neg_str = constraint.isNegated() ? " not " : " ";
                if (constraint.getReason() != null) rsn_str = " " + constraint.getReason();
            } else {
                EqualityIssue eq_iss = (EqualityIssue) iss;

                neg_str = eq_iss.isNegated() ? " not " : " ";

                ArgumentLocation other_loc = eq_iss.getLocation1();
                ActionStepModel step = steps.get(other_loc.getAtomIndex());

                //int idx = step.getActionDefinition().getParamNum(other_loc.getAccessors().get(0));
                ActionStreamEvent evt = step.getActionStreamEvent();

                List<String> otherAccessors = other_loc.getAccessors();
                int idx = evt.getDefinition().getParamNum(otherAccessors.get(0));
                StringBuffer otherAccessStr = new StringBuffer();
                for (int i = otherAccessors.size() - 1; i > 0; i--)
                	otherAccessStr.append(otherAccessors.get(i) + " of ");
                
                Object val = evt.getValue(idx);
                if (val instanceof Struct) { // don't print the whole struct!
                	val = evt.getDefinition().getParamName(idx);
                }
                vals.add(otherAccessStr.toString() + val.toString());

                //EqualityConstraint constraint = ((EqualityIssue) iss).getConstraint();
                //if (constraint.getReason() != null) rsn_str = " " + constraint.getReason();
            }

            for (int i = 0; i < vals.size(); i++) {
                if (i == 0)
                    val_str += "<i>" + vals.get(i) + "</i>";
                else if (i < vals.size() - 1)
                    val_str += ", <i>" + vals.get(i) + "</i>";
                else
                    val_str += " or <i>" + vals.get(i) + "</i>";
            }

            if (index >= value_equality_error_msgs.size())
                value_equality_error_msgs.add(new ArrayList<String>());

            value_equality_error_msgs.get(index).add("<i>" + loc.getAccessorString() + "</i> should" + neg_str + "be " + val_str + 
            		(rsn_str.equals("") ? "" : (" " + rsn_str)));
            process = true;
        }

        context.put("value_equality_trace_msgs", value_equality_trace_msgs);
        context.put("value_equality_error_msgs", value_equality_error_msgs);

        // Followed by any ordering issues
        List<List<String>> order_issue_msgs = new ArrayList<List<String>>();
        if (order_issues != null) {
            for (OrderingIssue iss : order_issues) {
                List<String> current = new ArrayList<String>();
                int pred_idx = iss.getPredecessorIndex();
                int succ_idx = iss.getSuccessorIndex();

                String sys_msg = "";

                if (pred_idx < 0)
                    sys_msg = "should be last";
                else if (succ_idx < 0)
                    sys_msg = "should be first";
                else
                    sys_msg = "should be before";

                if (pred_idx >= 0) {
                    ActionStepModel asm = new ActionStepModel(
                            actions.get(indexMap.get(pred_idx)));
                    current.add(processFancyName(asm.getFancyName()));
                }

                if (succ_idx >= 0) {
                    ActionStepModel asm = new ActionStepModel(
                            actions.get(indexMap.get(succ_idx)));
                    current.add(processFancyName(asm.getFancyName()));
                }

                if (current.size() == 1)
                    current.set(0, current.get(0) + " <b>" + sys_msg + "</b>");
                else
                    current.add(1, "<b>" + sys_msg + "</b>");

                order_issue_msgs.add(current);
                process = true;
            }
        }
        context.put("order_issue_msgs", order_issue_msgs);

        // Followed by "Other Problems"
        List<String> state_issue_msgs = new ArrayList<String>();

        if (state_issues != null) {
            for (StateIssue iss : state_issues) {
                try {
                    List<Object> values = getRuntimeArgs(symbols, iss,
                            indexMap, actions);
                    StateConstraint sc = iss.getConstraint();
                    if (sc.getFunctor().equals("contains")) {
                        PredicateEvaluator eval = tar.stateMap()
                                .getPredicateEvaluator_Java(sc.getFunctor());
                        String msg = eval.getErrorMessage(values,
                                sc.isNegated());

                        state_issue_msgs.add(msg);
                        process = true;
                    } else {
                        log.warn("Unsupported state constraint functor: "
                                + sc.getFunctor());
                    }
                } catch (PALException e) {
                    log.warn("Failed to verbalize failed state constraint", e);
                    e.printStackTrace();
                } catch (LumenSyntaxError e) {
                    log.warn("Failed to verbalize failed state constraint", e);
                    e.printStackTrace();
                }
            }
        }

        context.put("state_issue_msgs", state_issue_msgs);

        // Followed by unnecessary steps (extra atoms)
        List<String> extra_atom_msgs = new ArrayList<String>();
        if (extra_atom_issues != null && SHOW_EXTRA_ATOMS_ISSUES) {
            for (ExtraAtomsIssue iss : extra_atom_issues) {
                for (int i = iss.getStartAtomIndex(); i < iss.getEndAtomIndex(); i++) {
                    ActionStepModel asm = steps.get(i);
                    String s = processFancyName(asm.getFancyName());
                    extra_atom_msgs.add(s);
                    process = true;
                }
            }
        }

        context.put("extra_atom_msgs", extra_atom_msgs);

        if (!process)
            return null;

        StringWriter writer = new StringWriter();
        tmpl.merge(context, writer);

        String result = writer.toString();
        return result;
    }

    private StepModel createMissingAtomStepModel(SymbolManager symbols,
                                                 TaskSolution taskSolution,
                                                 final MissingAtomIssue iss,
                                                 final TaskAssessmentResult tar) {

        Bridge bridge = BackendFacade.getInstance().getBridge();

        Match match = tar.mtch();
        StepModel sm = null;
        ActionModelDef def = null;

        try {
            def = BackendFacade.getInstance().getType(
                    TypeNameFactory.makeName(iss.getFunctor()));
        } catch (PALException e) {
            log.error("Failed to look up missing step functor", e);
            return null;
        }

        if (def instanceof IdiomDef)
            sm = new IdiomStepModel(iss.getFunctor(), null, null);
        else
            sm = new ActionStepModel(iss.getFunctor(), null);

        final Step step = taskSolution.locateStep(iss.getStep());

        for (Parameter p : step.getAtom().getParameters()) {
            ParameterModel parmModel = null;
            for (ParameterModel param : sm.getInputs())
                if (param.getName().equals(p.getAccessor()))
                    parmModel = param;

            // Happens on outputs
            if (parmModel == null)
                continue;

            // Find a constraint we can use to help us visualize this missing
            // value
            ParamPatternNode ppn = (ParamPatternNode) match.pattern()
                    .getSecondaryNode(p.getId());
            for (Constraint c : ppn.getConstraints()) {
                if (c instanceof ValueEvaluator) {
                    ValueConstraint vc = ((ValueEvaluator) c).constraint();
                    if (vc.getValues().size() > 0) {
                        TypeDef type = ValueUtil.getTypeDef(
                                vc.getValues().get(0), bridge);
                        Object value = ValueUtil.getObject(vc.getValues()
                                .get(0), bridge);
                        parmModel.setTerm((TermModel) type.toAtr(
                                new CoreUIModelFactory(), value));
                    } else {
                        for (String ref : vc.getRefs()) {
                            Value value = symbols.lookupReference(ref);
                            if (value != null) {
                                TypeDef type = ValueUtil.getTypeDef(value,
                                        bridge);
                                Object val = ValueUtil.getObject(value, bridge);
                                TermModel tm = (TermModel) type.toAtr(
                                        new CoreUIModelFactory(), val);
                                parmModel.setTerm(tm);
                                break;
                            }
                        }
                    }
                    break;
                } else if (c instanceof EqualityEvaluator) {
                    EqualityConstraint ec = ((EqualityEvaluator) c)
                            .constraint();
                    boolean matched = false;
                    for (String paramId : ec.getParameters()) {
                        if (!paramId.equals(p.getId())) {
                            ArgCandidateNode arg = (ArgCandidateNode) match
                                    .getMappedSecondaryNode(paramId);
                            if (arg != null) {
                                TypeDef type = arg.getTypeDef();
                                Object val = arg.getValue();
                                parmModel.setTerm((TermModel) type.toAtr(
                                        new CoreUIModelFactory(), val));
                                matched = true;
                                break;
                            }
                        }
                    }
                    if (matched)
                        break;
                }

            }
        }

        return sm;
    }

    private List<Object> getRuntimeArgs(SymbolManager symbols,
                                        StateIssue iss,
                                        List<Integer> indexMap,
                                        List<ActionStreamEvent> actions)
            throws PALException,
            LumenSyntaxError {
        @SuppressWarnings({ "rawtypes", "unchecked" })
        ATRSyntax syntax = new ATRSyntax(new CoreUIModelFactory());

        List<ConstraintArgument> args = iss.getConstraint().getArguments();
        List<Object> values = new ArrayList<Object>();

        int locIdx = 0;

        for (int i = 0; i < args.size(); i++) {
            Object val = null;
            String ref = args.get(i).getRef();
            if (ref != null) {
                Value value = symbols.lookupReference(ref);
                if (value == null) {
                    ArgumentLocation loc = iss.getLocations().get(locIdx);
                    ActionStreamEvent event = actions.get(
                            indexMap.get(loc.getAtomIndex()));
                    val = event.getValue(loc.getAccessors().get(0));
                    locIdx++;
                }
            } else {
                Value value = args.get(0).getValue();
                String ctrs = value.getCtrs();
                ATRTerm term;

                term = (ATRTerm) syntax.termFromSource(ctrs);
                TypeDef type = ValueUtil.getTypeDef(value, bridge);
                val = type.fromAtr(term);
            }
            values.add(val);
        }

        return values;
    }

    private String processFancyName(List<Object> fancy) {
        StringBuffer buff = new StringBuffer();
        for (Object obj : fancy) {
            if (obj instanceof ParameterModel) {
                ParameterModel pm = (ParameterModel) obj;
                TypeDef type = pm.getTypeDef();
                try {
                    Object val = null;
                    if (type != null && val instanceof TermModel) {
                        val = type.fromAtr(pm.getTerm());
                        if (val == null)
                            val = "?";
                    } else {
                        val = pm.getTerm().getDisplayString();
                    }

                    buff.append("<i>" + val + "</i> ");
                } catch (PALException e) {
                    log.warn("Exception during fancy name processing", e);
                }
            } else
                buff.append(obj + " ");
        }
        return buff.toString();
    }

    @Override
    public void reset() {
        actionListener.reset();
    }

    private class ActionListener
            implements GlobalActionListener {
        private final List<ActionStreamEvent> actions = new ArrayList<>();

        @Override
        public synchronized void actionStarted(ActionStreamEvent action) {
            actions.add(action);
        }

        public synchronized List<ActionStreamEvent> getActions() {
            return actions;
        }

        public void reset() {
            actions.clear();
        }
    }
}
