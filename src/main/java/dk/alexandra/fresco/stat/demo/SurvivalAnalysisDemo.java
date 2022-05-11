package dk.alexandra.fresco.stat.demo;

import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.builder.numeric.field.MersennePrimeFieldDefinition;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.configuration.NetworkConfigurationImpl;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.network.socket.SocketNetwork;
import dk.alexandra.fresco.framework.sce.SecureComputationEngine;
import dk.alexandra.fresco.framework.sce.SecureComputationEngineImpl;
import dk.alexandra.fresco.framework.sce.evaluator.BatchedProtocolEvaluator;
import dk.alexandra.fresco.framework.sce.evaluator.EvaluationStrategy;
import dk.alexandra.fresco.framework.util.AesCtrDrbg;
import dk.alexandra.fresco.framework.value.SInt;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.lib.fixed.SFixed;
import dk.alexandra.fresco.stat.survival.SurvivalEntry;
import dk.alexandra.fresco.stat.survival.cox.CoxRegression;
import dk.alexandra.fresco.stat.utils.Triple;
import dk.alexandra.fresco.suite.spdz.SpdzProtocolSuite;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePool;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePoolImpl;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDummyDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzOpenedValueStoreImpl;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;

public class SurvivalAnalysisDemo {

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 6) {
      throw new IllegalArgumentException("Usage: java Demo [myId] [otherIP] [data] [mask] [time] [status]");
    }

    int timeVariable = Integer.parseInt(arguments[4]);
    int statusVariable = Integer.parseInt(arguments[5]);

    // Load data
    Iterable<CSVRecord> records = CSVFormat.DEFAULT.withRecordSeparator(",")
        .parse(new FileReader(arguments[2]));
    List<List<String>> data = StreamSupport.stream(records.spliterator(), false).map(
        record -> StreamSupport.stream(record.spliterator(), false).collect(Collectors.toList()))
//        .sorted(Comparator.comparingInt(a -> Integer.parseInt(a.get(timeVariable))))
        .collect(Collectors.toList());

    Iterable<CSVRecord> maskRecords = CSVFormat.DEFAULT.withRecordSeparator(",")
        .parse(new FileReader(arguments[3]));
    List<List<String>> mask = StreamSupport.stream(maskRecords.spliterator(), false).map(
        record -> StreamSupport.stream(record.spliterator(), false).collect(Collectors.toList()))
//        .sorted(Comparator.comparingInt(a -> Integer.parseInt(a.get(timeVariable))))
        .collect(Collectors.toList());

    java.util.Collections.reverse(data);
    java.util.Collections.reverse(mask);

    int n = data.size();
    if (mask.size() != n) {
      System.out.println("Mask and Data file must have the same number of rows (" + n + " != " + mask.size() + ")");
      return;
    }

    // Time variable
    List<Integer> time = IntStream.range(0, n).mapToObj(i -> {
      if (Integer.parseInt(mask.get(i).get(timeVariable)) == 1) {
        return Integer.parseInt(data.get(i).get(timeVariable));
      } else {
        return null;
      }
    }).collect(Collectors.toList());

    // Status variable
    List<Integer> censor = IntStream.range(0, n).mapToObj(i -> {
      if (Integer.parseInt(mask.get(i).get(statusVariable)) == 1) {
        return Integer.parseInt(data.get(i).get(statusVariable));
      } else {
        return null;
      }
    }).collect(Collectors.toList());

    // Number of covariates
    int p = data.get(0).size() - 2;

    List<List<Double>> covariates = IntStream.range(0, n).mapToObj(i -> IntStream.range(0, p + 2)
        .filter(j -> j != timeVariable && j != statusVariable)
        .mapToObj(j -> {
          if (Integer.parseInt(mask.get(i).get(j)) == 1) {
            return Double.parseDouble(data.get(i).get(j));
          } else {
            return null;
          }
        }).collect(Collectors.toList())).collect(Collectors.toList());

    // Configure fresco
    final int myId = Integer.parseInt(arguments[0]);
    final String otherIP = arguments[1];
    final int noParties = 2;
    final int otherId = 3 - myId;
    final int modBitLength = 256;
    final int maxBitLength = 180;
    final int maxBatchSize = 4096;

    Party me = new Party(myId, "localhost", 9000 + myId);
    Party other = new Party(otherId, otherIP, 9000 + otherId);
    NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId,
        Map.of(myId, me, otherId, other));
    Network network = new SocketNetwork(networkConfiguration);
    MersennePrimeFieldDefinition definition = MersennePrimeFieldDefinition.find(modBitLength);
    SpdzProtocolSuite suite = new SpdzProtocolSuite(maxBitLength);

    // Use "dummy" multiplication triples to simulate doing only the online phase
    SpdzDataSupplier supplier = new SpdzDummyDataSupplier(myId, noParties, definition,
        BigInteger.valueOf(1234));

    SpdzResourcePool resourcePool = new SpdzResourcePoolImpl(myId, noParties,
        new SpdzOpenedValueStoreImpl(), supplier,
        AesCtrDrbg::new);

    BatchedProtocolEvaluator<SpdzResourcePool> evaluator =
        new BatchedProtocolEvaluator<>(EvaluationStrategy.SEQUENTIAL_BATCHED.getStrategy(), suite,
            maxBatchSize);
    SecureComputationEngine<SpdzResourcePool, ProtocolBuilderNumeric> sce = new SecureComputationEngineImpl<>(
        suite, evaluator);

    System.out.println("========== Cox Regression ============");
    System.out.printf("My id:                           %5s%n", myId);
    System.out.printf("Other party URL: %21s%n", otherIP);
    System.out.printf("Observations:                    %5s%n", n);
    System.out.printf("Independent variables:           %5s%n", p);
    System.out.println("======================================");

    Instant start = Instant.now();

    CoxRegressionOutput out = sce
        .runApplication(new SurvivalAnalysisApplication(time, censor, covariates),
            resourcePool, network, Duration.ofMinutes(30));

    System.out.println(out);
    System.out.println("Took " + Duration.between(start, Instant.now()));
  }

  public static class SurvivalAnalysisApplication implements
      Application<CoxRegressionOutput, ProtocolBuilderNumeric> {

    private final List<Integer> time;
    private final List<Integer> censor;
    private final List<List<Double>> covariates;

    public SurvivalAnalysisApplication(List<Integer> time, List<Integer> censor,
        List<List<Double>> covariates) {
      this.time = time;
      this.censor = censor;
      this.covariates = covariates;
    }

    @Override
    public DRes<CoxRegressionOutput> buildComputation(ProtocolBuilderNumeric builder) {
      int n = time.size();
      return builder.par(par -> {
        int id = par.getBasicNumericContext().getMyId();
        int other = 3 - id;

        List<SurvivalEntry> structuredData = new ArrayList<>();

        for (int i = 0; i < n; i++) {
          DRes<SInt> secretTime =
              Objects.nonNull(time.get(i)) ? par.numeric().input(time.get(i), id)
                  : par.numeric().input(null, other);
          DRes<SInt> secretCensor =
              Objects.nonNull(censor.get(i)) ? par.numeric().input(censor.get(i), id)
                  : par.numeric().input(null, other);

          List<DRes<SFixed>> secretCovariates = covariates.get(i).stream()
              .map(xi -> Objects.nonNull(xi) ? FixedNumeric.using(par).input(xi, id)
                  : FixedNumeric.using(par).input(null, other)).collect(Collectors.toList());

          structuredData.add(new SurvivalEntry(secretCovariates, secretTime, secretCensor));
        }
        return DRes.of(structuredData);

      }).seq((seq, structuredData) -> new CoxRegression(structuredData, 3, 1.0,
          DoubleStream.generate(() -> 0.0).limit(structuredData.get(0).getCovariates().size())
              .toArray()).buildComputation(seq)).par(
          (par, result) -> DRes.of(new Triple<>(
              result.getModel().stream().map(FixedNumeric.using(par)::open)
                  .collect(Collectors.toList()),
              result.getStandardErrors().stream().map(FixedNumeric.using(par)::open)
                  .collect(Collectors.toList()),
              FixedNumeric.using(par).open(result.getPartialLikelihoodRatioTest())))
      ).seq(
          (seq, result) -> new CoxRegressionOutput(
              result.getFirst().stream().map(DRes::out).map(BigDecimal::doubleValue)
                  .collect(Collectors.toList()),
              result.getSecond().stream().map(DRes::out).map(BigDecimal::doubleValue)
                  .collect(Collectors.toList()),
              result.getThird().out().doubleValue(),
              n));
    }

    public String toString() {
      return "Cox Regression";
    }
  }

  private static class CoxRegressionOutput implements DRes<CoxRegressionOutput> {

    private final double G;
    private final List<Double> coefficients;
    private final List<Double> standardErrors;
    private final int n;

    private CoxRegressionOutput(List<Double> coefficients, List<Double> standardErrors, double G,
        int n) {
      this.coefficients = coefficients;
      this.standardErrors = standardErrors;
      this.G = G;
      this.n = n;
    }

    public String toString() {
      DecimalFormat df4 = new DecimalFormat("#.####");
      df4.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));

      StringBuilder sb = new StringBuilder();
      sb.append(
          "========================================================================================\n");
      sb.append("                               Cox Regression Results\n");
      sb.append(
          "========================================================================================\n");
      sb.append(
          String.format(
              "Model                                PH Reg    Sample size:                   %10s\n",
              n));
      double lrp = 1 - new ChiSquaredDistribution(coefficients.size()).cumulativeProbability(G);
      sb.append(String.format(
          "LR (Wilks):                      %10s    Prop(LR)                       %10s\n",
          df4.format(G), df4.format(lrp)));

      sb.append(
          "Ties:                               Breslow\n");
      sb.append(
          "========================================================================================\n");

      sb.append(
          "                log HR    std err         HR          z    P > |z|          95% CE\n");
      sb.append(
          "----------------------------------------------------------------------------------------\n");
      for (int i = 0; i < coefficients.size(); i++) {
        double z = coefficients.get(i) / standardErrors.get(i);
        double p = 2 * (1 - new NormalDistribution(0, 1).cumulativeProbability(Math.abs(z)));

        double z025 = new NormalDistribution(0, 1).inverseCumulativeProbability(1 - 0.025);
        double cel = coefficients.get(i) - z025 * standardErrors.get(i);
        double cer = coefficients.get(i) + z025 * standardErrors.get(i);
        sb.append("Coef ").append(i).append(
            String.format("      %10s %10s %10s %10s %10s %10s %10s\n",
                df4.format(coefficients.get(i)),
                df4.format(standardErrors.get(i)),
                df4.format(Math.exp(coefficients.get(i))),
                df4.format(z),
                df4.format(p),
                df4.format(cel),
                df4.format(cer)));

      }
      sb.append(
          "----------------------------------------------------------------------------------------\n");
      sb.append("Standard errors and confidence intervals are for log HR\n");
      sb.append(
          "----------------------------------------------------------------------------------------\n");

      return sb.toString();
    }

    @Override
    public CoxRegressionOutput out() {
      return this;
    }
  }

}