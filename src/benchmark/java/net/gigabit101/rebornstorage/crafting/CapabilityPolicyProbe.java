package net.gigabit101.rebornstorage.crafting;

/** Gives the isolated benchmark access to the live item capability decision. */
public final class CapabilityPolicyProbe {
  private CapabilityPolicyProbe() {}

  public static boolean plain() {
    return StackCapabilityPolicy.hooksArePlain();
  }
}
