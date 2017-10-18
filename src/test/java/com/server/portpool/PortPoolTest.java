package com.server.portpool;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.InvalidParameterException;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class PortPoolTest {
    private PortPool tester;

    @Before
    public void setUp() throws Exception {
        tester = new PortPool();
    }

    @Test
    public void whenPortPairIsAddedThenRuleWorksForBothPorts() throws Exception {
        tester.addRule(5000, 5001);
        assertEquals(5000, tester.getRule(5001));
        assertEquals(5001, tester.getRule(5000));
    }

    @Test
    public void whenNotExistentPortAccessedThenZeroIsReturned() throws Exception {
        assertEquals(0, tester.getRule(5000));
    }

    @Test (expected = InvalidParameterException.class)
    public void whenRuleExistsThenThrowException() throws Exception {
        tester.addRule(5000, 5001);
        tester.addRule(5000, 5002);
    }

    @Test
    public void whenGettingAllPortsThenPortsListedCorrectly() throws Exception {
        tester.addRule(5000, 5001);
        tester.addRule(5002, 5003);

        ArrayList<Integer> ports = tester.getPorts();
        ArrayList<Integer> expected = new ArrayList<>();

        expected.add(5000);
        expected.add(5001);
        expected.add(5002);
        expected.add(5003);

        assertEquals(expected, ports);
    }

    @Test
    public void whenDeleteMethodIsCalledThenRuleIsDeletedCorrectly() throws Exception {
        tester.addRule(5000, 5001);
        tester.addRule(5002, 5003);

        tester.deleteRule(5000);

        assertEquals(0, tester.getRule(5000));
        assertEquals(5003, tester.getRule(5002));
    }

    @Test
    public void whenDeletingNonExistentRuleThenNothingHappensAndWarningDisplayed() throws Exception {
        tester.addRule(5000, 5001);

        tester.deleteRule(1000);

        assertEquals(5001, tester.getRule(5000));
    }

    @After
    public void tearDown() throws Exception {
    }

}