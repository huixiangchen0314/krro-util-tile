package top.kzre.krro.util.tile;

/**
 * 版本标识能力。
 *
 * <p>COW 语义下，任何写入都会替换底层 data 引用——因此**引用本身就是
 * 版本**。本接口把这个约定显式化为能力契约，让应用层可以读取版本、
 * 用于 CAS 提交，而不必触碰 {@code getDataRef()}（那是包内可见的
 * 内部引用）。
 *
 * <p><b>返回值语义</b>：仅用于引用相等比较（{@code ==}）。不得读取
 * 内容、不得哈希、不得序列化——它只是某个时刻持有关系的身份凭证。
 *
 * <p><b>线程契约</b>：任意线程可读。返回的引用不持有所有权——
 * 调用方异步期间保存它做版本比对时，需自行保证对应 tile 仍存活。
 */
public interface VersionedTile {

    /** 逻辑数据标识。引用相等即同一份数据。默认返回自身。 */
    default Object version() {
        return this;
    }
}