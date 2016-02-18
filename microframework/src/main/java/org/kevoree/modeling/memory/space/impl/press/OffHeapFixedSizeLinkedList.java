package org.kevoree.modeling.memory.space.impl.press;

import org.kevoree.modeling.memory.chunk.impl.RandomUtil;
import org.kevoree.modeling.memory.chunk.impl.UnsafeUtil;
import sun.misc.Unsafe;

/**
 * @ignore ts
 * memory structure: | magic (4) | max (4) | head (4) | previous (size * 4) | next (size * 4) |
 */
public class OffHeapFixedSizeLinkedList implements PressFIFO {
    private static final Unsafe UNSAFE = UnsafeUtil.getUnsafe();

    private static final int ATT_MAGIC_LEN = 4;
    private static final int ATT_MAX_LEN = 4;
    private static final int ATT_HEAD_LEN = 4;
    private static final int ATT_PREVIOUS_ELEM_LEN = 4;
    private static final int ATT_NEXT_ELEM_LEN = 4;

    private static final int OFFSET_MAGIC = 0;
    private static final int OFFSET_MAX = 4;
    private static final int OFFSET_HEAD = OFFSET_MAX + ATT_MAX_LEN;

    private volatile long _start_address;

    public OffHeapFixedSizeLinkedList(long mem_addr, int max) {

        if (mem_addr == -1) {
            int mem = ATT_MAGIC_LEN + ATT_HEAD_LEN + max * ATT_PREVIOUS_ELEM_LEN + max * ATT_NEXT_ELEM_LEN;
            // allocate memory
            this._start_address = UNSAFE.allocateMemory(mem);

            UNSAFE.putInt(this._start_address + OFFSET_MAX, max);
            UNSAFE.putInt(this._start_address + OFFSET_HEAD, -1);
            UNSAFE.putInt(this._start_address + OFFSET_MAGIC, -1);
        } else {
            this._start_address = mem_addr;
        }
    }

    private long internal_get_offset_previous() {
        return OFFSET_HEAD + ATT_HEAD_LEN;
    }

    private long internal_get_offset_next() {
        int max = UNSAFE.getInt(this._start_address + OFFSET_MAX);
        return internal_get_offset_previous() + max * ATT_NEXT_ELEM_LEN;
    }

    @Override
    public void enqueue(int index) {
        int localMagic;
        do {
            localMagic = RandomUtil.nextInt();
        } while (!UNSAFE.compareAndSwapInt(null, this._start_address + OFFSET_MAGIC, -1, localMagic));

        if (UNSAFE.getInt(this._start_address + OFFSET_HEAD) == -1) {
            // set next(index) = index
            UNSAFE.putInt(this._start_address + internal_get_offset_next() + index * ATT_NEXT_ELEM_LEN, index);
            // set previous(index) = index
            UNSAFE.putInt(this._start_address + internal_get_offset_previous() + index * ATT_PREVIOUS_ELEM_LEN, index);
            // set head = index
            UNSAFE.putInt(this._start_address + OFFSET_HEAD, index);

        } else {
            int head = UNSAFE.getInt(this._start_address + OFFSET_HEAD);

            int currentHead = UNSAFE.getInt(this._start_address + OFFSET_HEAD);
            int currentPrevious = UNSAFE.getInt(this._start_address + internal_get_offset_previous() + head * ATT_PREVIOUS_ELEM_LEN);
            UNSAFE.putInt(this._start_address + OFFSET_HEAD, index);

            //chain previous
            UNSAFE.putInt(this._start_address + internal_get_offset_previous() + index * ATT_PREVIOUS_ELEM_LEN, currentPrevious);
            UNSAFE.putInt(this._start_address + internal_get_offset_next() + index * ATT_NEXT_ELEM_LEN, index);

            UNSAFE.putInt(this._start_address + internal_get_offset_previous() + currentHead * ATT_PREVIOUS_ELEM_LEN, index);
            UNSAFE.putInt(this._start_address + internal_get_offset_next() + index * ATT_NEXT_ELEM_LEN, currentHead);
        }

        UNSAFE.compareAndSwapInt(null, this._start_address + OFFSET_MAGIC, localMagic, -1);
    }

    @Override
    public int dequeue() {
        int localMagic;
        do {
            localMagic = RandomUtil.nextInt();
        } while (!UNSAFE.compareAndSwapInt(null, this._start_address + OFFSET_MAGIC, -1, localMagic));

        int currentHead = UNSAFE.getInt(this._start_address + OFFSET_HEAD);
        if (currentHead != -1) {
            //circular ring, take previous
            int tail = UNSAFE.getInt(this._start_address + internal_get_offset_previous() + currentHead * ATT_PREVIOUS_ELEM_LEN);
            int previous = UNSAFE.getInt(this._start_address + internal_get_offset_previous() + tail * ATT_PREVIOUS_ELEM_LEN);

            int _head = UNSAFE.getInt(this._start_address + OFFSET_HEAD);
            UNSAFE.putInt(this._start_address + internal_get_offset_next() + previous * ATT_NEXT_ELEM_LEN, _head);
            UNSAFE.putInt(this._start_address + internal_get_offset_previous() + _head * ATT_PREVIOUS_ELEM_LEN, previous);

            UNSAFE.compareAndSwapInt(null, this._start_address + OFFSET_MAGIC, localMagic, -1);
            return tail;
        } else {
            UNSAFE.compareAndSwapInt(null, this._start_address + OFFSET_MAGIC, localMagic, -1);
            return -1;
        }
    }


    public long getMemoryAddress() {
        return this._start_address;
    }

}