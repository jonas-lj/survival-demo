# Survival analysis demo

This repo contains a demonstration of how to use the survival analysis functionality in the <a href="">fresco-stat</a> framework.
Recall that fresco-stat allows multiple parties to analyse data distributed among them, wihtout any party learning anything about the other parties' data.

In this demonstration, we have two parties who have a combined data set consisting of a list of HIV patients. For each person, 
one party, say a hospital, knows if the patient has died, and how long after his or her diagnosis it happened. The hospital 
also knows the age of the patient. The other party knows whether the patient was a drug user or not. 

Now, the two parties wish to fit a <a hred="https://en.wikipedia.org/wiki/Proportional_hazards_model">proportional hazards</a> model on their combined data. The model will give an indication as to what influence age and drug 
usage will have on the probability of a patient dying. The data set is from the book "Applied Survival Analysis: Regression Modeling of Time-to-Event Data" by Stanley Lemeshow, Susanne May and David W. Hosmer Jr. and the corresponding analysis is done on page 105 in the book.

The application is built using the command <code>mvn package</code>. After this is done, the demo may be run in two terminals on the same computer using these commands:

```
java -jar target/survival.jar 1 localhost scaled1.csv mask1.csv 0 1
java -jar target/survival.jar 2 localhost scaled2.csv mask2.csv 0 1
```

In order, the parameters after the executable (survival.jar) are the party id,
the URL of the other party, the data set, the mask indicating what parts of
the data this party contributes with, the column containing the time of an
event and finally the column containing the status variable. 
The application will take a few minutes to complete and should output the following:

```
========================================================================
                        Cox Regression Results
========================================================================
Model                        PH Reg    Sample size:                  100
LR (Wilks):                 34.9819    Prop(LR)                        0
Ties:                       Breslow
========================================================================
          log HR   std err       HR        z   P > |z|      95% CE
------------------------------------------------------------------------
Coef 0    0.9156    0.1845   2.4982   4.9617         0   0.5539   1.2772
Coef 1    0.9415    0.2554   2.5639   3.6859    0.0002   0.4409   1.4422
------------------------------------------------------------------------
Standard errors and confidence intervals are for log HR
------------------------------------------------------------------------
```

Here, the first coefficient is for the age (scaled by 0.01), the second is for drug usage (1 yes, 0 no), 
so the regressions shows that an increase in age of 10 years increases the
mortality rate by 149.8% and that drug usage increases the mortality rate
of 156.4%.