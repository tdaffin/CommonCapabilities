package org.cyclops.commoncapabilities.modcompat.vanilla.capability.recipehandler;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.potion.PotionHelper;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.common.brewing.VanillaBrewingRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.cyclops.commoncapabilities.api.capability.recipehandler.*;
import org.cyclops.commoncapabilities.core.CompositeList;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recipe handler capability for the vanilla brewing stand.
 * @author rubensworks
 */
public class VanillaBrewingStandRecipeHandler implements IRecipeHandler {

    private static final Set<RecipeComponent<?, ?>> COMPONENTS_INPUT  = Sets.newHashSet(RecipeComponent.ITEMSTACK);
    private static final Set<RecipeComponent<?, ?>> COMPONENTS_OUTPUT = Sets.newHashSet(RecipeComponent.ITEMSTACK);
    private static final int[] OUTPUT_SLOTS = new int[] {1, 2, 3};
    private static final IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> EMPTY = new IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget>() {
        @Override
        public RecipeComponent<ItemStack, ItemHandlerRecipeTarget> getComponent() {
            return RecipeComponent.ITEMSTACK;
        }

        @Override
        public List<ItemStack> getMatchingInstances() {
            return Collections.emptyList();
        }

        @Override
        public boolean test(ItemStack itemStack) {
            return false;
        }
    };
    private static final IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> ANY = new IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget>() {
        @Override
        public RecipeComponent<ItemStack, ItemHandlerRecipeTarget> getComponent() {
            return RecipeComponent.ITEMSTACK;
        }

        @Override
        public List<ItemStack> getMatchingInstances() {
            return Collections.emptyList();
        }

        @Override
        public boolean test(ItemStack itemStack) {
            return true;
        }
    };

    private static List<RecipeDefinition> VANILLA_RECIPES = null;

    private final TileEntityBrewingStand tile;

    public VanillaBrewingStandRecipeHandler(TileEntityBrewingStand tile) {
        this.tile = tile;
    }

    @Override
    public Set<RecipeComponent<?, ?>> getRecipeInputComponents() {
        return COMPONENTS_INPUT;
    }

    @Override
    public Set<RecipeComponent<?, ?>> getRecipeOutputComponents() {
        return COMPONENTS_OUTPUT;
    }

    @Override
    public boolean isValidSizeInput(RecipeComponent component, int size) {
        return component == RecipeComponent.ITEMSTACK && (size >= 2 || size <= 4);
    }

    @Override
    public List<RecipeDefinition> getRecipes() {
        return new CompositeList<>(Lists.newArrayList(generateVanillaRecipes(),
                Lists.newArrayList(Iterables.filter(Lists.transform(BrewingRecipeRegistry.getRecipes(),
                        new Function<IBrewingRecipe, RecipeDefinition>() {
                            @Nullable
                            @Override
                            public RecipeDefinition apply(@Nullable IBrewingRecipe recipe) {
                                BrewingIngredientInput input;
                                BrewingIngredientIngredient ingredient;
                                IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> output;
                                if (recipe instanceof VanillaBrewingRecipe) {
                                    return null;
                                } else {
                                    input = new BrewingIngredientInput(recipe);
                                    ingredient = new BrewingIngredientIngredient(recipe);
                                    output = ANY;
                                }
                                return new RecipeDefinition(new RecipeIngredients(ingredient, input, input, input),
                                        new RecipeIngredients(EMPTY, output, output, output));
                            }
                        }), Predicates.notNull()))));
    }

    protected List<RecipeDefinition> generateVanillaRecipes() {
        if (VANILLA_RECIPES == null) {
            VANILLA_RECIPES = Lists.newArrayList();
            List<ItemStack> inputItems = Lists.newArrayList(PotionUtils.addPotionToItemStack(
                    new ItemStack(Items.POTIONITEM), PotionTypes.WATER));
            List<IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget>> ingredients = Lists.newArrayList();
            for (PotionHelper.MixPredicate<Item> mixPredicate : getPotionItems()) {
                ingredients.add(new RecipeIngredientItemStack(getMixReagent(mixPredicate)));
            }
            for (PotionHelper.MixPredicate<PotionType> mixPredicate : getPotionTypes()) {
                ingredients.add(new RecipeIngredientItemStack(getMixReagent(mixPredicate)));
            }

            List<ItemStack> checkInputItems = Lists.newArrayList(inputItems);
            while (!checkInputItems.isEmpty()) {
                List<ItemStack> newItems = Lists.newArrayList();
                for (ItemStack inputItem : checkInputItems) {
                    IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> item =
                            new RecipeIngredientItemStack(inputItem, true);
                    for (IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> ingredient : ingredients) {
                        ItemStack outputItem = PotionHelper.doReaction(Iterables.getFirst(ingredient
                                .getMatchingInstances(), ItemStack.EMPTY).copy(), inputItem.copy());
                        IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> output = new RecipeIngredientItemStack(
                                outputItem, true);
                        if (isPotionOutputValid(inputItem, outputItem)) {
                            addRecipeIfNew(ingredient, item, output, newItems);
                        }
                    }

                    // Throw error if infinite loop detected
                    if (VANILLA_RECIPES.size() > 7500) {
                        throw new RuntimeException("Infinite loop detected! Please report this to the Integrated " +
                                "Dynamics issue tracker with a list of (potion-changing) mods installed");
                    }
                }
                checkInputItems = newItems;
            }

        }
        return VANILLA_RECIPES;
    }

    protected static void addRecipeIfNew(IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> ingredient,
                                         IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> item,
                                         IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> output,
                                         List<ItemStack> newItems) {
        RecipeDefinition recipe = new RecipeDefinition(
                new RecipeIngredients(ingredient, item, item, item),
                new RecipeIngredients(EMPTY, output, output, output));
        if (!VANILLA_RECIPES.contains(recipe)) {
            VANILLA_RECIPES.add(recipe);
            newItems.addAll(output.getMatchingInstances());
        }
    }

    protected static boolean isPotionOutputValid(ItemStack input, ItemStack output) {
        return !input.isEmpty() && !output.isEmpty() && (input.getItem() != output.getItem()
                || (PotionUtils.getPotionFromItem(output) != PotionTypes.WATER
                && !Objects.equals(ForgeRegistries.POTION_TYPES.getKey(PotionUtils.getPotionFromItem(output)),
                ForgeRegistries.POTION_TYPES.getKey(PotionUtils.getPotionFromItem(input)))));
    }

    @Nullable
    @Override
    public RecipeIngredients simulate(RecipeIngredients input) {
        List<IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget>> recipeIngredients = input.getIngredients(RecipeComponent.ITEMSTACK);
        if (input.getComponents().size() != 1
                || !isValidSizeInput(RecipeComponent.ITEMSTACK, recipeIngredients.size())) {
            return null;
        }

        NonNullList<ItemStack> brewingItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
        for (int i = 0; i < recipeIngredients.size(); i++) {
            brewingItemStacks.set(i, Iterables.getFirst(recipeIngredients.get(i).getMatchingInstances(),
                    ItemStack.EMPTY).copy());
        }
        BrewingRecipeRegistry.brewPotions(brewingItemStacks, brewingItemStacks.get(0), OUTPUT_SLOTS);
        brewingItemStacks.set(0, ItemStack.EMPTY);

        return new RecipeIngredients(Lists.transform(brewingItemStacks,
                new Function<ItemStack, RecipeIngredientItemStack>() {
                    @Nullable
                    @Override
                    public RecipeIngredientItemStack apply(@Nullable ItemStack input) {
                        return new RecipeIngredientItemStack(input);
                    }
                }));
    }

    @Nullable
    @Override
    public <R> R[] getInputComponentTargets(RecipeComponent<?, R> component) {
        if (component == RecipeComponent.ITEMSTACK) {
            IItemHandler itemHandler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                    EnumFacing.NORTH);
            return (R[]) new ItemHandlerRecipeTarget[]{
                    new ItemHandlerRecipeTarget(itemHandler, 3),
                    new ItemHandlerRecipeTarget(itemHandler, 0),
                    new ItemHandlerRecipeTarget(itemHandler, 1),
                    new ItemHandlerRecipeTarget(itemHandler, 2)
            };
        }
        return null;
    }

    @Nullable
    @Override
    public <R> R[] getOutputComponentTargets(RecipeComponent<?, R> component) {
        if (component == RecipeComponent.ITEMSTACK) {
            IItemHandler itemHandler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                    EnumFacing.DOWN);
            return (R[]) new ItemHandlerRecipeTarget[]{
                    new ItemHandlerRecipeTarget(itemHandler, 3),
                    new ItemHandlerRecipeTarget(itemHandler, 0),
                    new ItemHandlerRecipeTarget(itemHandler, 1),
                    new ItemHandlerRecipeTarget(itemHandler, 2)
            };
        }
        return null;
    }

    private static List<PotionHelper.MixPredicate<PotionType>> getPotionTypes() {
        return ReflectionHelper.getPrivateValue(PotionHelper.class, null, "field_185213_a", "POTION_TYPE_CONVERSIONS");
    }

    private static List<PotionHelper.MixPredicate<Item>> getPotionItems() {
        return ReflectionHelper.getPrivateValue(PotionHelper.class, null, "field_185214_b", "POTION_ITEM_CONVERSIONS");
    }

    private static Ingredient getMixReagent(PotionHelper.MixPredicate<?> mixPredicate) {
        return ReflectionHelper.getPrivateValue(PotionHelper.MixPredicate.class, mixPredicate, "field_185199_b", "reagent");
    }

    public static class BrewingIngredientInput implements IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> {

        protected final IBrewingRecipe brewingRecipe;

        public BrewingIngredientInput(IBrewingRecipe brewingRecipe) {
            this.brewingRecipe = brewingRecipe;
        }

        @Override
        public RecipeComponent<ItemStack, ItemHandlerRecipeTarget> getComponent() {
            return RecipeComponent.ITEMSTACK;
        }

        @Override
        public List<ItemStack> getMatchingInstances() {
            return Collections.emptyList();
        }

        @Override
        public boolean test(ItemStack itemStack) {
            return brewingRecipe.isInput(itemStack);
        }
    }

    public static class BrewingIngredientIngredient implements IRecipeIngredient<ItemStack, ItemHandlerRecipeTarget> {

        protected final IBrewingRecipe brewingRecipe;

        public BrewingIngredientIngredient(IBrewingRecipe brewingRecipe) {
            this.brewingRecipe = brewingRecipe;
        }

        @Override
        public RecipeComponent<ItemStack, ItemHandlerRecipeTarget> getComponent() {
            return RecipeComponent.ITEMSTACK;
        }

        @Override
        public List<ItemStack> getMatchingInstances() {
            return Collections.emptyList();
        }

        @Override
        public boolean test(ItemStack itemStack) {
            return brewingRecipe.isIngredient(itemStack);
        }
    }
}
