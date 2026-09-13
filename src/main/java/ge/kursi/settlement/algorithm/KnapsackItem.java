package ge.kursi.settlement.algorithm;

/**
 * An item of the 0/1 knapsack problem in integer units.
 *
 * @param index  position of the item in the caller's list, used to map a solution back
 * @param weight capacity consumed if the item is taken (greater than 0)
 * @param value  value gained if the item is taken (0 or more)
 */
public record KnapsackItem(int index, long weight, long value) {

    public KnapsackItem {
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive, got " + weight);
        }
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative, got " + value);
        }
    }
}
