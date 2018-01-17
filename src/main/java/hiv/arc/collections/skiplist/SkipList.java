package hiv.arc.collections.skiplist;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.StringJoiner;

import javax.management.RuntimeErrorException;

public class SkipList<K, E> {

	private final int MAX_HEIGHT;
	private Elements.Row<E> topRow;
	private final Map<K, IElement<E>> map;
	private final Random r;

	int size;

	public SkipList() {
		this(0x1000);
	}

	/**
	 * @param size
	 *            The depth of the skip list depends on parameter {@code size}.
	 */
	public SkipList(int size) {
		this.MAX_HEIGHT = Math.max(5, (int) Math.log(size));
		topRow = Elements.createInitialRow();
		map = new HashMap<K, IElement<E>>(size);
		r = new Random(System.currentTimeMillis());
	}

	public void put(K key, E value, double score) {
		if (map.containsKey(key)) {
			remove(key);
		}
		insert(key, value, score);
	}

	public void remove(K key) {
		IElement<E> elem = map.get(key);

		IElement<E> cursol = elem;
		for (;;) {
			IElement<E> prevRight = cursol.getRight().orElseThrow(() -> SkipListError.ElementMustHaveRightElement);
			IElement<E> prevLeft = cursol.getLeft().orElseThrow(() -> SkipListError.ElementMustHaveLeftElement);

			prevLeft.setRight(prevRight);
			prevRight.setLeft(prevLeft);

			int skipNum = cursol.getSkipNum();
			prevRight.incrementSkipNum(skipNum - 1);

			if (cursol.getDown().isPresent()) {
				cursol = cursol.getDown().get();
			} else {
				break;
			}
		}

		cursol = elem;
		for (;;) {
			if (!cursol.getUp().isPresent() && !cursol.getRight().isPresent()) {
				break;
			}
			if (cursol.getUp().isPresent()) {
				cursol = cursol.getUp().get();
				cursol.incrementSkipNum(-1);
				continue;
			}
			if (cursol.getRight().isPresent()) {
				cursol = cursol.getRight().get();
			}
		}

		size--;
		map.remove(key);
	}

	private void insert(K key, E value, double score) {

		int height = randomHeight();

		IElement<E> newRight = getInertPointByScore(score);
		IElement<E> newLeft = newRight.getLeft().orElseThrow(() -> SkipListError.ElementMustHaveLeftElement);
		IElement<E> nextBottom;
		{
			IElement<E> newElem = new Element<E>(value, score);
			newElem.setLeft(newLeft);
			newElem.setRight(newRight);
			newElem.incrementSkipNum(1);
			newRight.setLeft(newElem);
			newLeft.setRight(newElem);
			nextBottom = newElem;
		}
		for (int i = 0; i < height; i++) {
			int distanceToLeft = nextBottom.getSkipNum();
			for (; !newRight.getUp().isPresent();) {
				if (!newRight.getRight().isPresent()) {
					topRow = Elements.createTopRow(topRow);
					topRow.mostRight.incrementSkipNum(size + 1);
					break;
				}
				newRight = newRight.getRight().get();
			}
			newRight = newRight.getUp().get();
			for (; !newLeft.getUp().isPresent();) {
				distanceToLeft += newLeft.getSkipNum();
				newLeft = newLeft.getLeft().orElseThrow(() -> SkipListError.ElementMustHaveLeftElement);
			}
			newLeft = newLeft.getUp().get();

			IElement<E> newElem = new Element<E>(value, score);
			newElem.setLeft(newLeft);
			newElem.setRight(newRight);
			newElem.setDown(nextBottom);
			nextBottom.setUp(newElem);
			newElem.incrementSkipNum(distanceToLeft);
			newRight.setLeft(newElem);
			newRight.incrementSkipNum(-distanceToLeft + 1);
			newLeft.setRight(newElem);
			nextBottom = newElem;
		}

		for (;;) {
			if (!newRight.getUp().isPresent() && !newRight.getRight().isPresent()) {
				break;
			}
			if (newRight.getUp().isPresent()) {
				newRight = newRight.getUp().get();
				newRight.incrementSkipNum(1);
				continue;
			}
			if (newRight.getRight().isPresent()) {
				newRight = newRight.getRight().get();
			}
		}
		size++;
		map.put(key, nextBottom);
	}

	public Optional<E> get(K key) {
		return _get(key).flatMap(elem -> elem.value());
	}

	public Optional<E> at(int i) {
		return _at(i).flatMap(elem -> elem.value());
	}

	public int getIndexByScore(double score) {
		IElement<E> current = topRow.mostLeft;
		int currentPos = 0;
		for (;;) {
			IElement<E> next = current.getRight().orElseThrow(() -> SkipListError.ElementMustHaveRightElement);
			if (next.getScore() < score) {
				current = next;
				currentPos += next.getSkipNum();
				continue;
			}
			if (!current.getDown().isPresent()) {
				return currentPos;
			}
			current = current.getDown().get();
		}
	}

	public Iterable<E> getIterableByRange(double minScoreInclusive, double maxScoreExclusive){
		int start = getIndexByScore(minScoreInclusive);
		
		Optional<IElement<E>> elem = _at(start);
		if(!elem.isPresent()){
			return new Iterable<E>(){
				@Override
				public Iterator<E> iterator() {
					return 	Collections.emptyIterator();
				}
			};
		}
		for(;elem.get().getDown().isPresent();){
			elem = elem.get().getDown();
		}
		
		final Optional<IElement<E>> initialElement = elem;
		return new Iterable<E>(){
			public Iterator<E> iterator(){
				return new Iterator<E>() {
					Optional<IElement<E>> next = initialElement; 
					@Override
					public boolean hasNext() {
						return next.isPresent() && next.get().value().isPresent() && next.get().getScore() < maxScoreExclusive;
					}

					@Override
					public E next() {
						if(!hasNext()){
							throw new IllegalStateException("Illiegal access for an iterator happened."+this.toString());
						}
						E result = next.get().value().get();
						next = next.get().getRight();
						return result;
					}
				};
			}
		};
	}

	private Optional<IElement<E>> _at(int i) {
		if (i < 0 || i >= size) {
			return Optional.empty();
		}
		int currentPos = -1;
		IElement<E> current = topRow.mostLeft;
		for (;;) {
			IElement<E> next = current.getRight().orElseThrow(() -> SkipListError.ElementMustHaveRightElement);
			for (;;) {
				if (currentPos + next.getSkipNum() <= i) {
					break;
				}
				current = current.getDown().orElseThrow(() -> SkipListError.ElementMustHaveDownElement);
				next = current.getRight().orElseThrow(() -> SkipListError.ElementMustHaveRightElement);
			}

			if (currentPos + next.getSkipNum() == i) {
				return Optional.of(next);
			}
			currentPos = currentPos + next.getSkipNum();
			current = next;
		}
	}

	private IElement<E> getInertPointByScore(double score) {
		IElement<E> current = topRow.mostLeft;
		for (;;) {
			IElement<E> next = current.getRight().orElseThrow(() -> SkipListError.ElementMustHaveRightElement);
			if (next.getScore() < score) {
				current = next;
				continue;
			}
			if (!current.getDown().isPresent()) {
				return next;
			}
			current = current.getDown().get();
		}
	}

	private Optional<IElement<E>> _get(K key) {
		if (map.containsKey(key)) {
			return Optional.of(map.get(key));
		} else {
			return Optional.empty();
		}
	}

	// return 0 with probability 1/2,
	// 1 with probability 1/4,
	// 2 with probability 1/8,
	// 3 with probability 1/16,
	// 4 with probability 1/32,
	// …
	// MAX_HEIGHT with probability 2^(-MAX_HEIGHT)
	private int randomHeight() {
		int bound = 1 << (MAX_HEIGHT - 1);
		int random = r.nextInt(bound);
		int count = 0;
		for (; (random & 1) == 1; count++) {
			random = random >> 1;
		}
		return count + 1;
	}

	String deepToString() {
		StringJoiner lines = new StringJoiner("\n", "{\n", "\n}");
		for (IElement<E> mostLeft = topRow.mostLeft;;) {
			StringJoiner sj = new StringJoiner(", ");

			for (IElement<E> current = mostLeft;;) {
				sj.add(current.deepToString());
				if (!current.getRight().isPresent()) {
					break;
				}
				current = current.getRight().get();
			}
			lines.add(sj.toString());

			if (!mostLeft.getDown().isPresent()) {
				break;
			}
			mostLeft = mostLeft.getDown().get();
		}
		return lines.toString();
	}

}

class SkipListError extends RuntimeErrorException {
	private static final long serialVersionUID = -9054212978445616068L;

	public SkipListError(Error e) {
		super(e);
	}

	public final static SkipListError ElementMustHaveRightElement = new SkipListError(
			new Error("ElementMustHaveRightElement"));
	public final static SkipListError ElementMustHaveLeftElement = new SkipListError(
			new Error("ElementMustHaveLeftElement"));
	public final static SkipListError ElementMustHaveDownElement = new SkipListError(
			new Error("ElementMustHaveDownElement"));
	public final static SkipListError IncrementSkipNumOfLeftSentinelMustNotBeCalled = new SkipListError(
			new Error("IncrementSkipNumOfLeftSentinelMustNotBeCalled"));
}

interface IElement<E> {
	Optional<IElement<E>> getUp();

	Optional<IElement<E>> getDown();

	Optional<IElement<E>> getLeft();

	Optional<IElement<E>> getRight();

	void setUp(IElement<E> upper);

	void setDown(IElement<E> bottom);

	void setLeft(IElement<E> left);

	void setRight(IElement<E> right);

	Optional<E> value();

	double getScore();

	int getSkipNum();

	void incrementSkipNum(int i);

	String deepToString();
}

class Element<E> implements IElement<E> {
	IElement<E> upper;
	IElement<E> bottom;
	IElement<E> left;
	IElement<E> right;

	final E _value;
	final double score;

	int skipNum;

	public Element(E _value, double score) {
		this._value = _value;
		this.score = score;
	}

	@Override
	public Optional<IElement<E>> getUp() {
		return Optional.ofNullable(upper);
	}

	@Override
	public Optional<IElement<E>> getDown() {
		return Optional.ofNullable(bottom);
	}

	@Override
	public Optional<IElement<E>> getLeft() {
		return Optional.of(left);
	}

	@Override
	public Optional<IElement<E>> getRight() {
		return Optional.of(right);
	}

	@Override
	public Optional<E> value() {
		return Optional.of(_value);
	}

	@Override
	public void setUp(IElement<E> upper) {
		this.upper = upper;
	}

	@Override
	public void setDown(IElement<E> bottom) {
		this.bottom = bottom;
	}

	@Override
	public void setLeft(IElement<E> left) {
		this.left = left;
	}

	@Override
	public void setRight(IElement<E> right) {
		this.right = right;
	}

	@Override
	public double getScore() {
		return score;
	}

	@Override
	public int getSkipNum() {
		return skipNum;
	}

	@Override
	public void incrementSkipNum(int i) {
		skipNum += i;
	}

	@Override
	public String deepToString() {
		return String.format("[val: %s. score: %g, skip: %d]", value(), getScore(), getSkipNum());
	}

}

abstract class SentinelElement<E> implements IElement<E> {
	IElement<E> upper;
	IElement<E> bottom;
	IElement<E> left;
	IElement<E> right;

	@Override
	public Optional<IElement<E>> getUp() {
		return Optional.ofNullable(upper);
	}

	@Override
	public Optional<IElement<E>> getDown() {
		return Optional.ofNullable(bottom);
	}

	@Override
	public Optional<IElement<E>> getLeft() {
		return Optional.ofNullable(left);
	}

	@Override
	public Optional<IElement<E>> getRight() {
		return Optional.ofNullable(right);
	}

	@Override
	public Optional<E> value() {
		return Optional.empty();
	}

	@Override
	public void setUp(IElement<E> upper) {
		this.upper = upper;
	}

	@Override
	public void setDown(IElement<E> bottom) {
		this.bottom = bottom;
	}

	@Override
	public void setLeft(IElement<E> left) {
		this.left = left;
	}

	@Override
	public void setRight(IElement<E> right) {
		this.right = right;
	}

	static class Left<E> extends SentinelElement<E> {
		@Override
		public double getScore() {
			return -Double.MAX_VALUE;
		}

		@Override
		public int getSkipNum() {
			return 0;
		}

		@Override
		public void incrementSkipNum(int i) {
			throw SkipListError.IncrementSkipNumOfLeftSentinelMustNotBeCalled;
		}

		@Override
		public String deepToString() {
			return String.format("[LEFT. score: %g, skip: %d]", getScore(), getSkipNum());
		}

	}

	static class Right<E> extends SentinelElement<E> {
		@Override
		public double getScore() {
			return Double.MAX_VALUE;
		}

		int skipNum;

		@Override
		public int getSkipNum() {
			return skipNum;
		}

		@Override
		public void incrementSkipNum(int i) {
			skipNum += i;
		}

		@Override
		public String deepToString() {
			return String.format("[RIGHT. score: %g, skip: %d]", getScore(), getSkipNum());
		}
	}

}

class Elements {

	static class Row<E> {
		final IElement<E> mostLeft;
		final IElement<E> mostRight;

		public Row(IElement<E> mostLeft, IElement<E> mostRight) {
			this.mostLeft = mostLeft;
			this.mostRight = mostRight;
		}

	}

	static <A> Row<A> createTopRow(Row<A> currentTopRow) {
		SentinelElement<A> left = new SentinelElement.Left<A>();
		left.bottom = currentTopRow.mostLeft;
		SentinelElement<A> right = new SentinelElement.Right<A>();
		right.bottom = currentTopRow.mostRight;
		left.right = right;
		right.left = left;

		left.bottom.setUp(left);
		right.bottom.setUp(right);

		return new Row<A>(left, right);
	}

	static <A> Row<A> createInitialRow() {
		SentinelElement<A> left = new SentinelElement.Left<A>();
		SentinelElement<A> right = new SentinelElement.Right<A>();
		left.right = right;
		right.left = left;

		return new Row<A>(left, right);
	}

}
