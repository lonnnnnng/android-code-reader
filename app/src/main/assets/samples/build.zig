const std = @import("std");

pub fn build(b: *std.Build) void {
    const exe = b.addExecutable(.{ .name = "reader", .root_source_file = b.path("src/main.zig") });
    b.installArtifact(exe);
}
