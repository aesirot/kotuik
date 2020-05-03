package backtest.pesok;

import backtest.Bar;
import backtest.CSVHistoryLoader;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;


/**
 * The Class JfreeCandlestickChartDemo.
 *
 * @author ashraf
 */
@SuppressWarnings("serial")
public class JfreeCandlestickChartDemo extends JPanel {

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowGUI() {
        //Make sure we have nice window decorations.
        JFrame.setDefaultLookAndFeelDecorated(true);

        //Create and set up the window.
        JFrame frame = new JFrame("JfreeCandlestickChartDemo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the chart.
        JfreeCandlestickChart jfreeCandlestickChart = new JfreeCandlestickChart("TWTR");

        ArrayList<Bar> bars = new CSVHistoryLoader().load("", "RU000A0JXQ93");
        for (int i = 0; i<10;i++) {
            Bar bar = bars.get(i);
            jfreeCandlestickChart.addCandel(bar.getDatetime(), bar.getOpen().doubleValue(),
                    bar.getHigh().doubleValue(), bar.getLow().doubleValue(), bar.getClose().doubleValue(), bar.getVolume());
        }

        frame.setContentPane(jfreeCandlestickChart);

        //Disable the resizing feature
        frame.setResizable(false);
        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        //creating and showing this application's GUI.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}