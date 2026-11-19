package com.aventura.teleradio.media;

import com.aventura.teleradio.TeleRadioMod;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Radio, televisor o pantalla gigante. Click derecho abre el control (en el cliente). */
public class MediaBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public enum Tipo {
		/** Solo audio. */
		RADIO,
		/** Televisor con pantalla incorporada. */
		TELEVISOR,
		/** Panel delgado: la imagen se agranda hasta 16x9 bloques. */
		PANTALLA;

		public boolean video() {
			return this != RADIO;
		}
	}

	/** Lo pone el cliente: abre la pantalla de control de este bloque. */
	public static Consumer<BlockPos> abrirControl = pos -> {
	};
	/** Lo pone el cliente: abre el control de los audifonos / parlante. */
	public static Runnable abrirPortatil = () -> {
	};

	private final Tipo tipo;
	private final Map<Direction, VoxelShape> formas;

	public MediaBlock(Tipo tipo, BlockBehaviour.Properties properties) {
		super(properties);
		this.tipo = tipo;
		VoxelShape forma = switch (tipo) {
			case RADIO -> Block.box(2, 0, 4, 14, 9, 12);
			case TELEVISOR -> Block.box(1, 0, 2, 15, 13, 14);
			case PANTALLA -> Block.box(0, 0, 14, 16, 16, 16);
		};
		this.formas = Shapes.rotateHorizontal(forma);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public Tipo tipo() {
		return this.tipo;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		// la pantalla se pega a la pared en la que se hace click
		if (this.tipo == Tipo.PANTALLA && context.getClickedFace().getAxis().isHorizontal()) {
			facing = context.getClickedFace();
		}
		return this.defaultBlockState().setValue(FACING, facing);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.formas.get(state.getValue(FACING));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			abrirControl.accept(pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MediaBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, TeleRadioMod.MEDIA, MediaBlockEntity::tickServidor);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
