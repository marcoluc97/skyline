// SPDX-License-Identifier: MPL-2.0
// Copyright © 2021 Skyline Team and Contributors (https://github.com/skyline-emu/)

#pragma once

#include <boost/container/stable_vector.hpp>
#include <renderdoc_app.h>
#include <common/linear_allocator.h>
#include <gpu/megabuffer.h>
#include "command_nodes.h"

namespace skyline::gpu::interconnect {
    /*
     * @brief Thread responsible for recording Vulkan commands from the execution nodes and submitting them
     */
    class CommandRecordThread {
      public:
        /**
         * @brief Single execution slot, buffered back and forth between the GPFIFO thread and the record thread
         */
        struct Slot {
            /**
             * @brief Helper to begin the slot command buffer on the cycle waiter thread
             */
            struct ScopedBegin {
                Slot &slot;

                ScopedBegin(Slot &slot);

                ~ScopedBegin();
            };

            vk::raii::CommandPool commandPool; //!< Use one command pool per slot since command buffers from different slots may be recorded into on multiple threads at the same time
            vk::raii::CommandBuffer commandBuffer;
            vk::raii::Fence fence;
            vk::raii::Semaphore semaphore;
            std::shared_ptr<FenceCycle> cycle;
            boost::container::stable_vector<node::NodeVariant> nodes;
            LinearAllocatorState<> allocator;
            std::mutex beginLock;
            std::condition_variable beginCondition;
            u32 executionNumber;
            bool ready{}; //!< If this slot's command buffer has had 'beginCommandBuffer' called and is ready to have commands recorded into it
            bool capture{}; //!< If this slot's Vulkan commands should be captured using the renderdoc API

            Slot(GPU &gpu);

            Slot(Slot &&other);

            /**
             * @brief Waits on the fence and resets the command buffer
             * @note A new fence cycle for the reset command buffer
             */
            std::shared_ptr<FenceCycle> Reset(GPU &gpu);

            /**
             * @brief Waits for the command buffer to be began so it can be recorded into
             */
            void WaitReady();

            void Begin();
        };

      private:
        const DeviceState &state;
        CircularQueue<Slot *> incoming; //!< Slots pending recording
        CircularQueue<Slot *> outgoing; //!< Slots that have been submitted, may still be active on the GPU

        std::thread thread;

        void ProcessSlot(Slot *slot);

        void Run();

      public:
        CommandRecordThread(const DeviceState &state);

        /**
         * @return A free slot, `Reset` needs to be called before accessing it
         */
        Slot *AcquireSlot();

        /**
         * @brief Submit a slot to be recorded
         */
        void ReleaseSlot(Slot *slot);
    };

    /**
     * @brief Assembles a Vulkan command stream with various nodes and manages execution of the produced graph
     * @note This class is **NOT** thread-safe and should **ONLY** be utilized by a single thread
     */
    class CommandExecutor {
      private:
        const DeviceState &state;
        GPU &gpu;
        CommandRecordThread recordThread;
        CommandRecordThread::Slot *slot{};
        node::RenderPassNode *renderPass{};
        size_t subpassCount{}; //!< The number of subpasses in the current render pass
        u32 renderPassIndex{};
        bool preserveLocked{};

        /**
         * @brief A wrapper of a Texture object that has been locked beforehand and must be unlocked afterwards
         */
        struct LockedTexture {
            std::shared_ptr<Texture> texture;

            explicit LockedTexture(std::shared_ptr<Texture> texture);

            LockedTexture(const LockedTexture &) = delete;

            constexpr LockedTexture(LockedTexture &&other);

            constexpr Texture *operator->() const;

            ~LockedTexture();
        };

        std::vector<LockedTexture> preserveAttachedTextures;
        std::vector<LockedTexture> attachedTextures; //!< All textures that are attached to the current execution

        /**
         * @brief A wrapper of a Buffer object that has been locked beforehand and must be unlocked afterwards
         */
        struct LockedBuffer {
            std::shared_ptr<Buffer> buffer;

            LockedBuffer(std::shared_ptr<Buffer> buffer);

            LockedBuffer(const LockedBuffer &) = delete;

            constexpr LockedBuffer(LockedBuffer &&other);

            constexpr Buffer *operator->() const;

            ~LockedBuffer();
        };

        std::vector<LockedBuffer> preserveAttachedBuffers;
        std::vector<LockedBuffer> attachedBuffers; //!< All textures that are attached to the current execution


        std::vector<TextureView *> lastSubpassAttachments; //!< The storage backing for attachments used in the last subpass
        span<TextureView *> lastSubpassInputAttachments; //!< The set of input attachments used in the last subpass
        span<TextureView *> lastSubpassColorAttachments; //!< The set of color attachments used in the last subpass
        TextureView *lastSubpassDepthStencilAttachment{}; //!< The depth stencil attachment used in the last subpass

        std::vector<std::function<void()>> flushCallbacks; //!< Set of persistent callbacks that will be called at the start of Execute in order to flush data required for recording
        std::vector<std::function<void()>> pipelineChangeCallbacks; //!< Set of persistent callbacks that will be called after any non-Maxwell 3D engine changes the active pipeline

        void RotateRecordSlot();

        /**
         * @brief Create a new render pass and subpass with the specified attachments, if one doesn't already exist or the current one isn't compatible
         * @param noSubpassCreation Forces creation of a renderpass when a new subpass would otherwise be created
         * @note This also checks for subpass coalescing and will merge the new subpass with the previous one when possible
         * @return If the next subpass must be started prior to issuing any commands
         */
        bool CreateRenderPassWithSubpass(vk::Rect2D renderArea, span<TextureView *> sampledImages, span<TextureView *> inputAttachments, span<TextureView *> colorAttachments, TextureView *depthStencilAttachment, bool noSubpassCreation = false);

        /**
         * @brief Ends a render pass if one is currently active and resets all corresponding state
         */
        void FinishRenderPass();

        /**
         * @brief Execute all the nodes and submit the resulting command buffer to the GPU
         * @note It is the responsibility of the caller to handle resetting of command buffers, fence cycle and megabuffers
         */
        void SubmitInternal();

        /**
         * @brief Resets all the internal state, this must be called before starting a new submission as it clears everything from a past submission
         */
        void ResetInternal();

        void AttachBufferBase(std::shared_ptr<Buffer> buffer);

      public:
        std::shared_ptr<FenceCycle> cycle; //!< The fence cycle that this command executor uses to wait for the GPU to finish executing commands
        LinearAllocatorState<> *allocator;
        ContextTag tag; //!< The tag associated with this command executor, any tagged resource locking must utilize this tag
        size_t submissionNumber{};
        u32 executionNumber{};
        bool captureNextExecution{};

        CommandExecutor(const DeviceState &state);

        ~CommandExecutor();

        /**
         * @brief Attach the lifetime of the texture to the command buffer
         * @return If this is the first usage of the backing of this resource within this execution
         * @note The supplied texture will be locked automatically until the command buffer is submitted and must **not** be locked by the caller
         * @note This'll automatically handle syncing of the texture in the most optimal way possible
         */
        bool AttachTexture(TextureView *view);

        /**
         * @brief Attach the lifetime of a buffer view to the command buffer
         * @return If this is the first usage of the backing of this resource within this execution
         * @note The supplied buffer will be locked automatically until the command buffer is submitted and must **not** be locked by the caller
         * @note This'll automatically handle syncing of the buffer in the most optimal way possible
         */
        bool AttachBuffer(BufferView &view);

        /**
         * @brief Attach the lifetime of a buffer view that's already locked to the command buffer
         * @note The supplied buffer **must** be locked with the executor's tag
         * @note There must be no other external locks on the buffer aside from the supplied lock
         * @note This'll automatically handle syncing of the buffer in the most optimal way possible
         */
        void AttachLockedBufferView(BufferView &view, ContextLock<BufferView> &&lock);

        /**
         * @brief Attach the lifetime of a buffer object that's already locked to the command buffer
         * @note The supplied buffer **must** be locked with the executor's tag
         * @note There must be no other external locks on the buffer aside from the supplied lock
         * @note This'll automatically handle syncing of the buffer in the most optimal way possible
         */
        void AttachLockedBuffer(std::shared_ptr<Buffer> buffer, ContextLock<Buffer> &&lock);

        /**
         * @brief Attach the lifetime of the fence cycle dependency to the command buffer
         */
        void AttachDependency(const std::shared_ptr<void> &dependency);

        /**
         * @brief Adds a command that needs to be executed inside a subpass configured with certain attachments
         * @param exclusiveSubpass If this subpass should be the only subpass in a render pass
         * @note Any supplied texture should be attached prior and not undergo any persistent layout transitions till execution
         */
        void AddSubpass(std::function<void(vk::raii::CommandBuffer &, const std::shared_ptr<FenceCycle> &, GPU &, vk::RenderPass, u32)> &&function, vk::Rect2D renderArea, span<TextureView *> sampledImages, span<TextureView *> inputAttachments = {}, span<TextureView *> colorAttachments = {}, TextureView *depthStencilAttachment = {}, bool noSubpassCreation = false);

        /**
         * @brief Adds a subpass that clears the entirety of the specified attachment with a color value, it may utilize VK_ATTACHMENT_LOAD_OP_CLEAR for a more efficient clear when possible
         * @note Any supplied texture should be attached prior and not undergo any persistent layout transitions till execution
         */
        void AddClearColorSubpass(TextureView *attachment, const vk::ClearColorValue &value);

        /**
         * @brief Adds a subpass that clears the entirety of the specified attachment with a depth/stencil value, it may utilize VK_ATTACHMENT_LOAD_OP_CLEAR for a more efficient clear when possible
         * @note Any supplied texture should be attached prior and not undergo any persistent layout transitions till execution
         */
        void AddClearDepthStencilSubpass(TextureView *attachment, const vk::ClearDepthStencilValue &value);

        /**
         * @brief Adds a command that needs to be executed outside the scope of a render pass
         */
        void AddOutsideRpCommand(std::function<void(vk::raii::CommandBuffer &, const std::shared_ptr<FenceCycle> &, GPU &)> &&function);

        /**
         * @brief Adds a persistent callback that will be called at the start of Execute in order to flush data required for recording
         */
        void AddFlushCallback(std::function<void()> &&callback);

        /**
         * @brief Adds a persistent callback that will be called after any non-Maxwell 3D engine changes the active pipeline
         */
        void AddPipelineChangeCallback(std::function<void()> &&callback);

        /**
         * @brief Calls all registered pipeline change callbacks
         */
        void NotifyPipelineChange();

        /**
         * @brief Execute all the nodes and submit the resulting command buffer to the GPU
         */
        void Submit();

        /**
         * @brief Locks all preserve attached buffers/textures
         * @note This **MUST** be called before attaching any buffers/textures to an execution
         */
        void LockPreserve();

        /**
         * @brief Unlocks all preserve attached buffers/textures
         * @note This **MUST** be called when there is no GPU work left to be done to avoid deadlocks where the guest will try to lock a buffer/texture but the GPFIFO thread has no work so won't periodically unlock it
         */
        void UnlockPreserve();
    };
}
